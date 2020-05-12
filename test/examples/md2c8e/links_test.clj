(ns md2c8e.links-test
  (:require [clojure.java.io :as io :refer [file]]
            [clojure.test :refer [are deftest is testing]]
            [md2c8e.confluence :as c8e]
            [md2c8e.links :as links]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths :refer [path]]))

(deftest test-page?
  (are [expected given] (= expected (#'links/page? given))
    false nil
    false {}
    false ""
    false {:page-id 0}
    false {::c8e/id 0}
    true  {::c8e/page-id 0}))

(defn- page
  [title ^String fp & children]
  {::c8e/page-id nil
   ::c8e/title title
   ::md/source {::md/fp (file fp)}
   ::md/children (or children [])})

(deftest test-page-titles-by-path
  (testing "All the keys, which are paths, should be normalized to be relative to the source-dir"
    (let [source-dir "/tmp/docs"
          pt (page "Root" "/tmp/docs" (page "Technologies" "/tmp/docs/technologies/"))
          expected {(path "") "Root"
                    (path "technologies") "Technologies"}]
      (is (= expected (#'links/page-titles-by-path pt source-dir))))))
