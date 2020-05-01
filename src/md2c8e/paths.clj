(ns md2c8e.paths
  (:require [clojure.java.io :refer [file]])
  (:import [java.io File]
           [java.nio.file Path]))

(defn path
  "Accepts a path as a File or String; returns a Path."
  [fp]
  (condp instance? fp
    Path fp ; just in case it’s already a path.
    File (.toPath fp)
    String (.toPath (file fp))))

(defn absolute-path
  "Accepts a path as a Path, File, or String; returns an absolute Path."
  [fp]
  (.toAbsolutePath (path fp)))

(defn relative-path
  "Accepts paths as Path, File, or String objects; returns a relative Path."
  [from to]
  (.relativize (absolute-path from) (absolute-path to)))
