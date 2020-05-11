(ns md2c8e.links
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [md2c8e.confluence :as c8e]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths]))

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
  (-> (.getParent (paths/path from))
      (.resolve (paths/path href))
      (.normalize)
      (->> (.relativize base))))

(defn- has-scheme?
  "We don’t want to even try to replace links that start with a scheme, such as http, mailto, etc."
  [html]
  (boolean (re-seq #"href=\".+:.+\"" html)))

(defn- link->confluence
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
    (let [href (some-> (re-find #"href=\"(.+)\"" html) second)
          body (some-> (re-find #">(.+)<" html) second)
          resolved (resolve-link href sfp base-path)
          target-title (get lookup resolved)]
      (if-not target-title
        html ;; TODO: this is basically just silently failing, which BTW is bad.
        (format "<ac:link>
                 <ri:page ri:content-title=\"%s\" />
                 <ac:plain-text-link-body>
                  <![CDATA[%s]]>
                 </ac:plain-text-link-body>
                 </ac:link>" target-title body)))))

(defn replace-links
  [page-tree source-dir]
  (let [lookup (page-titles-by-path page-tree source-dir)
        base-path (paths/path source-dir)]
    (walk/postwalk
      (fn [v]
        (if-not (and (page? v) (::c8e/body v))
          v
          (let [sfp (get-in v [::md/source ::md/fp])]
            (println "Processing:" (::c8e/title v))
            (update v ::c8e/body (fn [body]
                                          (str/replace body
                                                       #"<a.+</a>"
                                                       #(link->confluence % sfp base-path lookup)))))))
      page-tree)))
