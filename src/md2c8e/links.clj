(ns md2c8e.links
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [md2c8e.confluence :as c8e]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths :refer [path relative-path]])
  (:import [java.net URI]))

(defn- page?
  [v]
  (and (map? v)
       (contains? v ::c8e/page-id))) ;; maybe use children? or a spec?

(defn- page-seq
  [page-tree]
  (tree-seq page? ::md/children page-tree))

(defn- page-titles-by-path
  "We use this lookup to resolve links."
  [page-tree source-dir]
  (->> (page-seq page-tree)
       (filter ::md/source)
       (map (fn [page] (vector (paths/relative-path source-dir (get-in page [::md/source ::md/fp]))
                               (::c8e/title page))))
       (into {})))

(defn- resolve-link
  "Given a “to” relative path, the “from” path of the context from which it was extracted, and the
  base path for the “from“ path, return the path from the base to the “to”. Returns a Path.

  For example: (resolve-link ../bla/foo.md /tmp/docs/sys/gl.md /tmp/docs) -> bla/foo.md"
  [href from base]
  (-> (.getParent (path from))
      (.resolve (path href))
      (.normalize)
      (->> (.relativize base))))

(def href-pattern
  "Extracts the URL from an HTML link."
  #"href=\"(.+)\"")

(defn- has-scheme?
  "We don’t want to even try to replace links that start with a scheme, such as http, mailto, etc."
  [html]
  (some? (some-> (re-find href-pattern html)
                 (second)
                 (URI.)
                 (.getScheme))))

(defn- link->c8e
  "Given an HTML link such as <a href='url'>text</a> returns a Confluence link such as:
   <ac:link>
   <ri:page ri:content-title='Page Title' />
   <ac:plain-text-link-body>
    <![CDATA[Link to another Confluence Page]]>
   </ac:plain-text-link-body>
   </ac:link>

   Yes, really.

   Passes the href values to `resolve-link` to replace the relative URLs with Confluence page
   titles."
  [html sfp base-path lookup]
  (if (has-scheme? html)
    html
    (let [href (some-> (re-find href-pattern html) second)
          body (some-> (re-find #">(.+)<" html) second)
          resolved (resolve-link href sfp base-path)
          target-title (get lookup resolved)]
      (if-not target-title
        (do (println (format "| %s | %s |" (relative-path base-path sfp) href))
            html)
        (format "<ac:link>
                 <ri:page ri:content-title=\"%s\" />
                 <ac:plain-text-link-body>
                  <![CDATA[%s]]>
                 </ac:plain-text-link-body>
                 </ac:link>" target-title body)))))

(def link-pattern
  ;; Might want try https://github.com/lambdaisland/regal at some point
  #"<a[^>]+>.*?</a>")

(defn- replace-body-links
  [body sfp base-path lookup]
  (str/replace body link-pattern (fn [link] (link->c8e link sfp base-path lookup))))

(defn replace-links
  [page-tree source-dir]
  (let [lookup (page-titles-by-path page-tree source-dir)
        base-path (path source-dir)]
    (println (str "🚨 Failed link replacements:\n\n"
                  "| File | Link |\n"
                  "| ---- | ---- |"))
    (walk/postwalk
      (fn [v]
        (if-let [sfp (and (page? v)
                          (::c8e/body v)
                          (get-in v [::md/source ::md/fp]))]
          (update v ::c8e/body #(replace-body-links % sfp base-path lookup))
          v))
      page-tree)))
