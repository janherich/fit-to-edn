(ns fit-to-edn.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer [chan <!! >!! close!]]
            [fit-to-edn.record :as record]
            [fit-to-edn.queries :as q]
            [fit-to-edn.format :as f])
  (:import [java.io File FileInputStream InputStream]
           [com.garmin.fit Decode MesgBroadcaster RecordMesg RecordMesgListener FitRuntimeException]))

(set! *warn-on-reflection* true)

(defn last-val
  "Exhausts channel, returning the last value taken from it."
  [ch]
  (loop [v (<!! ch)
         last-val v]
    (if v
      (recur (<!! ch) v)
      last-val)))

(defn read-fit-records
  "Reads records from fit binary file."
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
  "Returns sequence of fit files."
  [directory-path]
  (->> directory-path
       io/file
       file-seq
       (filter (fn [^File fit]
                 (re-find #".*.fit" (.getName fit))))))

(defn query-file
  "Queries one file with specified query-transducer, which is expected to return just
  single result. If format-fn is specified, final result will be run through the format-fn."
  ([xf fit-file]
   (query-file xf fit-file nil))
  ([xf fit-file format-fn]
   (when-let [result (-> (read-fit-records xf fit-file)
                         last-val)]
     (cond-> result
       format-fn format-fn))))

(defn query-files
  "Queries multiple files with specified query-transducer, which is expected to return
  just single result (for each file). Results are returned as sequence of
  [query-result file-object] tuples, if aggregate-fn is specified, results will be
  passed to it."
  ([xf fit-files]
   (query-files xf nil nil fit-files))
  ([xf aggregate-fn fit-files]
   (query-files xf aggregate-fn nil fit-files))
  ([xf aggregate-fn format-fn fit-files]
   (when-let [results (->> fit-files
                           (pmap (juxt (partial query-file xf) identity))
                           (filter (comp identity first)))]
     (cond-> results
       aggregate-fn aggregate-fn
       format-fn format-fn))))
