(ns md2c8e.markdown
  (:require [clojure.string :as str])
  (:import [java.io File]
    [org.commonmark.parser Parser]
    [org.commonmark.renderer.html HtmlRenderer]))

(def md-h1-pattern #"(?m)^# (.+)$")

(defn- extract-title
  [^String md]
  (some-> (re-find md-h1-pattern (or md ""))
          (second)
          (str/trim)
          (str/replace #"[^A-Za-z0-9 ():_-]" "")))

(defn file->page-title
  "NB: fp could be a dir, in which case fc will be nil."
  [^File fp ^String fc]
  (or (extract-title fc)
      (str/capitalize (.getName fp))))

(defn- remove-title
  [^String md]
  (str/replace md md-h1-pattern ""))

(def parser-cache (atom nil))

(defn- parser
  []
  (or @parser-cache
      (reset! parser-cache (.build (Parser/builder)))))

(def renderer-cache (atom nil))

(defn- renderer
  []
  (or @renderer-cache
      (reset! renderer-cache (.build (HtmlRenderer/builder)))))

(defn- markdown->html
  [md]
  (let [doc (.parse (parser) md)
        res (.render (renderer) doc)]
    res))

(defn prep-content
  [md]
  (-> (remove-title md)
      (markdown->html)))
