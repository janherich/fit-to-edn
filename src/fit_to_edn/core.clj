(ns fit-to-edn.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer [chan <!! >!! close!]]
            [fit-to-edn.record :as record]
            [fit-to-edn.transducers :as t])
  (:import [java.io File FileInputStream InputStream]
           [com.garmin.fit Decode MesgBroadcaster RecordMesg RecordMesgListener FitRuntimeException]))

(set! *warn-on-reflection* true)

(defn last-val
  "Exhausts channel, returning the last value taken from it"
  [ch]
  (loop [v (<!! ch)
         last-val v]
    (if v
      (recur (<!! ch) v)
      last-val)))

(defn read-fit-records
  "Reads records from fit binary file"
  ([^File fit]
   (read-fit-records nil fit))
  ([xf ^File fit]
   (let [^InputStream in (io/input-stream fit)
         out (if xf (chan 1 xf) (chan))
         listener (reify RecordMesgListener
                    (^void onMesg [this ^RecordMesg mesg]
                     (>!! out (record/parse-record-mesg mesg))))
         ^Decode decode (Decode.)
         ^MesgBroadcaster mesgBroadcaster (doto (MesgBroadcaster. decode)
                                            (.addListener ^RecordMesgListener listener))]
     (future
       (try
         (.read decode in mesgBroadcaster mesgBroadcaster)
         (catch FitRuntimeException e
           (prn (format "Exception decoding file %s : %s" (.getPath fit) (.getMessage e))))
         (finally
           (.close in)
           (close! out))))
     out)))

(defn list-fit-files
  "Returns sequence of fit files"
  [directory-path]
  (->> directory-path
       io/file
       file-seq
       (filter (fn [^File fit]
                 (re-find #".*.fit" (.getName fit))))))

(comment
  "Bryton fit records may include multiple consecutive records with the same timestamp
   but different attrs, so we should merge them before further processing"
  (def records '({:timestamp 1}
                 {:timestamp 1
                  :power 0}
                 {:timestamp  1
                  :speed  2}
                 {:timestamp 1
                  :temperature 20}
                 {:timestamp 2
                  :power 1}
                 {:timestamp  2
                  :speed 1}
                 {:timestamp 3
                  :speed 2})))

(defn- process-files
  [xf format-fn fit-files]
  (->> fit-files
       (pmap (comp last-val
                   (partial read-fit-records xf)))
       (filter identity) ;; filter out records which crashed/did return nil during processing
       (reduce max)
       float
       format-fn))

(defn query-max
  "Churn through provided fit files and find the maximum metrics attained over interval
   specified in seconds, or if interval is not specified, it's average over whole activity"
  ([metrics format-fn fit-files]
   (query-max metrics format-fn fit-files nil))
  ([metrics format-fn fit-files interval]
   (let [base-transducers [(t/combine-records merge) (keep metrics)]
         process-transducers (condp = interval
                               nil [(t/avg-transducer)]
                               1   [(t/max-transducer)]
                               [(t/avg-transducer interval) (t/max-transducer)])
         xf (apply comp (concat base-transducers process-transducers))]
     (process-files xf format-fn fit-files))))

(defn- moving
  [metrics]
  (fn [{:keys [speed] :as data}]
    (when (and (metrics data)
               speed
               (> speed 0))
      (metrics data))))

(def max-power (partial query-max
                        :power
                        (partial format "%.3f watt")))

(def max-speed (partial query-max
                        #(some->> % :speed (* 3.6))
                        (partial format "%.3f km/h")))
