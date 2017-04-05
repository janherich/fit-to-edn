(ns fit-to-edn.transducers)

(defn- update-window
  [[sum queue] window-size item]
  (let [conjoined (conj queue item)
        summed (+ sum item)]
    (if (> (count conjoined) window-size)
      [(- summed (first conjoined)) (pop conjoined)]
      [summed conjoined])))

(defn avg-transducer
  "Stateful transducer returning average over n consecutive numbers from numbers input,
  if no window of size n is specified (0-arity), returns (current) average over already
  consumed numbers input."
  ([]
   (fn [xf]
     (let [avg (volatile! [0 0])]
       (completing
        (fn [result input]
          (let [[sum count] (vswap! avg (juxt (comp (partial + input) first)
                                              (comp inc second)))]
            (xf result (/ sum count))))))))
  ([window-size]
   (fn [xf]
     (let [avg (volatile! [0 (clojure.lang.PersistentQueue/EMPTY)])]
       (completing
        (fn [result input]
          (let [[avg-sum avg-window] (vswap! avg update-window window-size input)]
            (if (= window-size (count avg-window))
              (xf result (/ avg-sum window-size))
              result))))))))

(defn- power
  [exp base]
  (Math/pow base exp))

(defn- root
  [root base]
  (power (/ 1.0 root) base))

(defn normalized-transducer
  "Stateful transducer returning normalized average over n consecutive numbers from number input,
  if no window of size n is specified (0-arity), returns (current) normalized average over
  already consumed numbers input.
  Normalizing is performed according to the original NP algorithm from Dr. Andrew Coggan -
  http://www.peakscoachinggroup.com/DrAndrewCoggan"
  ([]
   (normalized-transducer nil))
  ([window-size]
   (let [avg-xf (if window-size
                  (avg-transducer window-size)
                  (avg-transducer))]
     (comp
      (avg-transducer 30)
      (map (partial power 4))
      avg-xf
      (map (partial root 4))))))

(defn max-transducer
  "Stateful transducer returning maximum item from numbers input."
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

(defn combine-records-transducer
  "Stateful transducer combining consequent records according to combine-metrics."
  [combine-metrics combine-fn]
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
         (let [prior-record @previous-record
               prior-metrics (combine-metrics prior-record)]
           (if (or (not prior-metrics)
                   (= prior-metrics (combine-metrics input)))
             ;; accumulate when combine-metrics is not yet set/not changing
             (do (vswap! previous-record combine-fn input)
                 result)
             ;; whenever new combine-metrics comes in, reduce the accumulated record
             (do (vreset! previous-record input)
                 (xf result prior-record)))))))))
