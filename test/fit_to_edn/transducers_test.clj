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

(deftest normalized-transducer-test
  (testing "normalized transducer result should not differ for static input (same numbers)"
    (is (= (sequence (normalized-transducer) (repeat 35 10))
           (repeat 6 10.0))))
  (testing "normalized transducer returns correct output for variable input,
            which is higher then arithmetic average"
    (is (= (sequence (normalized-transducer 5) (take 35 (iterate inc 0)))
           '(16.67925430743545 17.669273869990874)))))

(deftest combine-records-test
  (testing "combine-records transducer combines together records according to combine metrics"
    (is (= (sequence (combine-records-transducer :t merge) '({:t 0 :p 0}
                                                             {:t 0 :s 0}
                                                             {:t 1 :p 100 :s 10}
                                                             {:t 2 :p 150}
                                                             {:t 2 :s 15}
                                                             {:t 3 :p 200 :s 20}))
           '({:t 0 :p 0 :s 0}
             {:t 1 :p 100 :s 10}
             {:t 2 :p 150 :s 15}
             {:t 3 :p 200 :s 20})))))
