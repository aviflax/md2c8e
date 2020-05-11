(ns md2c8e.links-test
  (:require [clojure.test :refer [are deftest is testing]]
            [md2c8e.confluence :as c8e]
            [md2c8e.links :as links]
            [md2c8e.paths :as paths :refer [path]]
            [md2c8e.test-utils :refer [page]]))

(deftest test-page?
  (are [expected given] (= expected (#'links/page? given))
    false nil
    false {}
    false ""
    false {:page-id 0}
    false {::c8e/id 0}
    true  {::c8e/page-id 0}))

(deftest test-page-titles-by-path
  (testing "All the keys, which are paths, should be normalized to be relative to the source-dir"
    (let [source-dir "/tmp/docs"
          pt (page "Root" "/tmp/docs" (page "Technologies" "/tmp/docs/technologies/"))
          expected {(path "") "Root"
                    (path "technologies") "Technologies"}]
      (is (= expected (#'links/page-titles-by-path pt source-dir))))))

(deftest test-link->confluence
  (testing "Links to URLs that specify protocols (schemes) should be passed through untouched."
    (are [expected given] (= expected (#'links/link->confluence given nil nil nil))
      "<a href=\"http://zombo.com\">Zombocom</a>"
      "<a href=\"http://zombo.com\">Zombocom</a>"

      "<a href=\"mailto:avi.flax@fundingcircle.com\">Avi Flax</a>"
      "<a href=\"mailto:avi.flax@fundingcircle.com\">Avi Flax</a>")))
