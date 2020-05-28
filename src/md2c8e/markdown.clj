(ns md2c8e.markdown
  (:require [clojure.string :as str])
  (:import [java.io File]
    [org.commonmark.parser Parser]
    [org.commonmark.renderer.html HtmlRenderer]
    [org.commonmark.ext.autolink AutolinkExtension]
    [org.commonmark.ext.gfm.headinganchor HeadingAnchorExtension]
    [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
    [org.commonmark.ext.gfm.tables TablesExtension]))

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

(def extensions
  [(AutolinkExtension/create)
   (HeadingAnchorExtension/create)
   (StrikethroughExtension/create)
   (TablesExtension/create)])

(def parser-cache (atom nil))
(def renderer-cache (atom nil))

(defn- parser
  []
  (or @parser-cache
      (reset! parser-cache (-> (Parser/builder)
                               (.extensions extensions)
                               (.build)))))

(defn- renderer
  []
  (or @renderer-cache
      (reset! renderer-cache (-> (HtmlRenderer/builder)
                                 (.extensions extensions)
                                 (.build)))))

(defn- markdown->html
  [md]
  (let [doc (.parse (parser) md)
        res (.render (renderer) doc)]
    res))

(defn prep-content
  [md]
  (-> (remove-title md)
      (markdown->html)))
