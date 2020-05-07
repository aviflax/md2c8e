(ns md2c8e.io
  (:import [java.io File]))

(defn page-files
  "Given a File pointer to a directory, returns a sequence of files that should be converted to
  pages, including directories. Filters out various undesired files, e.g. hidden files."
  [^File fp]
  {:pre [(.isDirectory fp)]}
  (remove (memfn isHidden) (.listFiles fp)))
