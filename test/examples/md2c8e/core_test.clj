(ns md2c8e.core-test
  (:require [clojure.java.io :as io :refer [file]]
            [clojure.test :refer [are deftest is testing]]
            [md2c8e.core :as core]
            [md2c8e.markdown :as md]
            [md2c8e.test-utils :refer [page]]))

(deftest test-readme?
  (are [expected given] (= expected (#'core/readme? given))
    true (page "Cheeses" "/docs/cheeses/README.md")
    true (page "Foo" "readme.md")
    false (page "Trick" "/docs/readme.md/wut")
    false "readme.md" ; not a page
    false (file "readme.md") ; not a page
    false ""
    false 42
    false nil))

(deftest test-integrate-readme
  (testing (str "A directory containing a README should have its title, content, and source replaced"
                "  by that of the README.")
    (let [readme (page "Technologies" "/tmp/docs/techs/README.md")
          pt (page "techs" "/tmp/docs/techs/" readme)
          expected (assoc (page "Technologies" "/tmp/docs/techs/README.md" readme)
                          ::md/content-replaced-by readme)]
      (is (= expected (#'core/integrate-readme pt))))))
