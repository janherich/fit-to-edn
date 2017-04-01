(ns fit-to-edn.core-test
  (:require [clojure.test :refer :all]
            [fit-to-edn.core :refer :all]
            [clojure.core.async :as async :refer [chan put! <!! close!]]))

(deftest last-val-test
  (testing "taking last value from the channel works"
    (let [c (chan)]
      (put! c 1)
      (put! c 2)
      (put! c 3)
      (let [v (future (last-val c))]
        (close! c)
        (is (= 3 @v))))))
