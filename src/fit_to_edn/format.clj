(ns fit-to-edn.format)

(def power
  (comp (partial format "%.3f watt") float))

(def cadence
  (comp (partial format "%d rpm") int))

(def heart-rate
  (comp (partial format "%d bpm" int)))

(def speed-kmh
  (comp (partial format "%.3f km/h") (partial * 3.6) float))

(def speed-mph
  (comp (partial format "%.3f mph") (partial * 2.369) float))

(defn format-query-result
  ([result-format-fn]
   (fn [[result]]
     (result-format-fn result)))
  ([result-format-fn file-format-fn]
   (fn [[result fit-file]]
     {:query-result (result-format-fn result)
      :activity (file-format-fn fit-file)})))
