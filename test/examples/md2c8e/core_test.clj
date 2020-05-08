(ns md2c8e.core-test
  (:require [clojure.java.io :as io :refer [file]]
            [clojure.string :as str :refer [ends-with?]]
            [clojure.test :refer [are deftest is testing]]
            [md2c8e.confluence :as c8e]
            [md2c8e.core :as core]
            [md2c8e.markdown :as md]))

(defn- page
  [title ^String fp & children]
  {::c8e/page-id nil
   ::c8e/title title
   ::md/source {::md/fp (file fp)
                ::md/is-file (not (ends-with? fp "/"))}
   ::md/children (or children [])})

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
  (testing (str "A directory containing a README should have its title and content replaced by that"
                " of the README, but its source path should be unchanged.")
    ;; This is important because of link resolution, which happens in md2c8e.links/replace-links.
    ;; The link targets in the Markdown source will often point to directories,
    ;; e.g. [Repos][../repos/] which need to be replaced with the title of the page in the tree
    ;; that represents that directory. That page entity will have its title and content replaced
    ;; with that of its README, if it exists, but it’s important that its source file *not* be
    ;; replaced so that that link resolution process will succeed.
    ;;
    ;; TODO: We should probably also have a corresponding test for links that target the README.md
    ;; directly — we need to have a corresponding entry in our page tree for that as well. This
    ;; is not uncommon when one wants to link to an anchor in a page; a link like
    ;; repos/readme.md#introduction just looks better than repos/#introduction.
    (let [p (page "techs"
                  "/tmp/docs/techs/"
                  (page "Technologies" "/tmp/docs/techs/README.md"))
          expected (assoc (page "Technologies" "/tmp/docs/techs")
                          ::md/replaced-by-readme true)]
      (is (= expected (#'core/integrate-readme p))))))
