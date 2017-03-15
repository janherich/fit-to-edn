(ns fit-to-edn.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer [chan <!! >!! close!]]
            [fit-to-edn.record :as record])
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

(defn- dropping-conj
  [queue window-size item]
  (let [conjoined (conj queue item)]
    (if (> (count conjoined) window-size)
      (pop conjoined)
      conjoined)))

(defn avg-transducer
  "Stateful transducer returning average over n consecutive numbers from numbers input"
  [window-size]
  (fn [xf]
    (let [avg-window (volatile! (clojure.lang.PersistentQueue/EMPTY))]
      (completing
       (fn [result input]
         (let [new-avg (vswap! avg-window dropping-conj window-size input)]
           (if (= window-size (count new-avg))
             (xf result (/ (reduce + new-avg) window-size))
             result)))))))

(defn max-transducer
  "Stateful transducer returning maximum item from numbers input"
  []
  (fn [xf]
    (let [max (volatile! 0)]
      (completing
       (fn [result input]
         (let [prev-max @max]
           (if (> input prev-max)
             (do (vreset! max input)
                 (xf result input))
             (xf result prev-max))))))))

(defn combine-records
  "Stateful transducer combining records with the same timestamp"
  ([combine-fn]
   (fn [xf]
     (let [previous-record (volatile! {})]
       (fn
         ([] (xf))
         ([result]
          ;; completing arity needs to reduce the last accumulated record
          (let [p-r @previous-record
                result (if (empty? p-r)
                         result
                         (do
                           (vreset! previous-record {})
                           (unreduced (xf result p-r))))]
            (xf result)))
         ([result input]
          (let [{:keys [timestamp] :as prior-record} @previous-record]
            (if (or (not timestamp)
                    (= timestamp (:timestamp input)))
              ;; accumulate when timestamp is not yet set/not changing
              (do (vswap! previous-record combine-fn input)
                  result)
              ;; whenever new timestamp comes in, reduce the accumulated record
              (do (vreset! previous-record input)
                  (xf result prior-record)))))))))
  ([combine-fn coll]
   (sequence (combine-records combine-fn) coll)))

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

(defn max-power-interval
  "Churn through provided fit files and find the maximum power attained over interval specified
   in seconds"
  [fit-files interval]
  (->> fit-files
       (pmap (comp last-val
                   (partial read-fit-records (comp
                                              (combine-records merge)
                                              (keep :power)
                                              (avg-transducer interval)
                                              (max-transducer)))))
       (filter identity)
       (reduce max)
       float))
