(ns md2c8e.test-utils
  (:require [clojure.java.io :as io :refer [file]]
            [clojure.string :as str :refer [ends-with?]]
            [md2c8e.confluence :as c8e]
            [md2c8e.markdown :as md]))

(defn page
  [title ^String fp & children]
  {::c8e/page-id nil
   ::c8e/title title
   ::md/source {::md/fp (file fp)
                ::md/is-file (not (ends-with? fp "/"))}
   ::md/children (or children [])})
