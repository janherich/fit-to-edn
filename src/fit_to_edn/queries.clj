(ns fit-to-edn.queries
  (:require [fit-to-edn.transducers :as t]))

(defn- non-zero
  [non-zero-metrics metrics]
  (fn [data]
    (when-let [v (non-zero-metrics data)]
      (when (> v 0)
        (metrics data)))))

(def moving (partial non-zero :speed))

(defn- base
  [metrics]
  (comp
   (t/combine-records-transducer :timestamp merge)
   (keep metrics)))

(def power (base :power))

(def cadence (base :cadence))

(def heart-rate (base :heart-rate))

(def moving-power (base (moving :power)))

(def non-coasting-power (base (non-zero :power :power)))

(def speed (base :speed))

(def moving-speed (base (moving :speed)))

(defn- max-interval
  [agg interval]
  (if (= 1 interval)
    (t/max-transducer)
    (comp (agg interval) (t/max-transducer))))

(def max-average-interval (partial max-interval t/avg-transducer))

(def max-normalized-interval (partial max-interval t/normalized-transducer))

(def average (t/avg-transducer))

(def normalized-average (t/normalized-transducer))

(defn max-aggregate
  "Returns result with maximal result value form collection of [result file] tuples"
  [results]
  (->> results
       (sort-by first >)
       (first)))
