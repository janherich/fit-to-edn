(ns fit-to-edn.transducers-test
  (:require [clojure.test :refer :all]
            [fit-to-edn.transducers :refer :all]))

(deftest max-transducer-test
  (testing "max transducer always returns current maximum from input"
    (is (= (sequence (max-transducer) '(1 3 2 1 4 5 3 7 6))
           '(1 3 3 3 4 5 5 7 7)))))

(deftest avg-transducer-test
  (testing "avg transducer returns current (moving) average over specified window"
    (is (= (sequence (avg-transducer 4) '(1 2 3 4 5 6 7 8 9 10))
           '(5/2 7/2 9/2 11/2 13/2 15/2 17/2))))
  (testing "avg transducer returns average over whole input if no window is specified"
    (is (= (sequence (avg-transducer) '(1 2 3 4 5 6 7 8 9 10))
           '(1 3/2 2 5/2 3 7/2 4 9/2 5 11/2)))))
