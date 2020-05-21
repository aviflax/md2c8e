(ns md2c8e.confluence-test
  (:require [clojure.test :refer [deftest is testing]]
            [md2c8e.confluence :as c8e]))

(deftest test-get-page-space-key
  (testing "When the page-id is NOT in the cache"
    (reset! (deref #'c8e/page-id->space-key) {})
    (let [page-id 123
          space-key "~foo.bar"
          retrieved? (atom false)
          actual (with-redefs [c8e/get-page-by-id (fn [page-id' _client]
                                                    (is (= page-id page-id'))
                                                    (reset! retrieved? true)
                                                    {:space {:key space-key}})]
                   (#'c8e/get-page-space-key page-id nil))]
      (is (= space-key actual))
      (is @retrieved? "the page should be retrieved from Confluence")
      (is (instance? String actual) "the result should be a string")
      (is (= {page-id space-key} @@#'c8e/page-id->space-key) "the cache should be populated")))
  (testing "When the page-id IS in the cache"
    (let [page-id 123
          space-key "~foo.bar"
          cache-val {page-id space-key}
          _ (reset! (deref #'c8e/page-id->space-key) cache-val)
          retrieved? (atom false)
          actual (with-redefs [c8e/get-page-by-id (fn [page-id' _client]
                                                    (is (= page-id page-id'))
                                                    (reset! retrieved? true)
                                                    {:space {:key space-key}})]
                   (#'c8e/get-page-space-key page-id nil))]
      (is (= space-key actual))
      (is (not @retrieved?) "the page should not be retrieved from Confluence")
      (is (instance? String actual) "the result should be a string")
      (is (= cache-val @@#'c8e/page-id->space-key) "the cache should be unchanged"))))
