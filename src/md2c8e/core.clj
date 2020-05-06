(ns md2c8e.core
  (:require [clojure.string :as str :refer [ends-with? lower-case]]
            [clojure.walk :as walk]
            [cognitect.anomalies :as anom]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.confluence :as confluence :refer [upsert]]
            [md2c8e.io :as io]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths]
            [medley.core :as mc :refer [find-first]])
  (:import [java.io File]))

(defn- file->page
  [^File fp] ;; fp == file-pointer (can point to dirs too)
  (let [file-contents (when (.isFile fp) (slurp fp))
        title (md/file->page-title fp file-contents)]
    (println "Loading" title)
    {::confluence/page-id nil
     ::confluence/title title
     ::confluence/body (when (.isFile fp) (md/prep-content file-contents))
     ::md/source {::md/fp fp ;; fp == file-pointer (can point to dirs too)
                  ::md/contents file-contents
                  ::md/is-dir (.isDirectory fp)
                  ::md/is-file (.isFile fp)}
     ::md/children (if (.isDirectory fp)
                    (mapv file->page (io/page-files fp))
                    [])}))

(defn- readme?
  [{:keys [::md/source] :as _page}]
  (and (::is-file source)
       (ends-with? (lower-case (::md/fp source)) "readme.md")))

(defn- integrate-readmes
  "Checks each child to see if its filename is README.md (case-insensitive). If it is, it copies its
  title and content to the parent, then removes that child from the sequence of children. Finally,
  calls itself recursively on all children that represent directories."
  [page]
  (if-let [readme (find-first readme? (::md/children page))]
    (-> (update page ::md/children #(remove readme? %))
        (merge (select-keys readme [::confluence/title ::confluence/body ::md/source])) ;; TODO: is this brittle?
        (assoc ::md/replaced-by-readme true
               ::md/original-source (::md/source page)))
    page))

(defn dir->page-tree
  "Returns a tree of pages as per file->page."
  [^File source-dir root-page-id]
  {:pre [(.isDirectory source-dir)]}
  {::confluence/page-id root-page-id
   ::md/children (->> (pmap file->page (io/page-files source-dir)) ; using mapv because file->page does IO
                   (map integrate-readmes)
                   (doall))})

(defn validate
  "Returns a sequence of errors. For example, if two pages have the same title, the sequence includes an
  anom."
  [page-tree]
  (println "TODO: implement validate!!!")
  [{::anom/type :fault
    ::anom/message "TODO: implement validate!!!"}])

(defn- page?
  [v]
  (and (map? v)
       (contains? v ::confluence/page-id))) ;; maybe use children? or a spec?

(defn- page-seq
  [page-tree]
  (tree-seq page? ::md/children page-tree))

(defn- page-titles-by-path
  "We use this lookup to resolve links."
  [page-tree source-dir]
  (->> (page-seq page-tree)
       (filter ::md/source)
       (map (fn [page] (vector (paths/relative-path source-dir (get-in page [::md/source ::md/fp]))
                               (::confluence/title page))))
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
    (let [href (some-> (re-find #"href=\"(.+)\"" html) second)
          body (some-> (re-find #">(.+)<" html) second)
          resolved (resolve-link href sfp base-path)
          target-title (get lookup resolved)]
      ; (prn "Found match:" href "for page" (str sfp) "resolved to " target-title)
      (if-not target-title
        html
        (format "<ac:link>
                 <ri:page ri:content-title=\"%s\" />
                 <ac:plain-text-link-body>
                  <![CDATA[%s]]>
                 </ac:plain-text-link-body>
                 </ac:link>" target-title body))))

(defn replace-links
  [page-tree source-dir]
  (let [lookup (page-titles-by-path page-tree source-dir)
        base-path (paths/path source-dir)]
    (walk/postwalk
      (fn [v]
        (if-not (and (page? v) (::confluence/body v))
          v
          (let [sfp (get-in v [::md/source ::md/fp])]
            (println "Processing:" (::confluence/title v))
            (update v ::confluence/body (fn [body]
                               (str/replace body
                                            #"<a.+</a>"
                                            #(link->confluence % sfp base-path lookup)))))))
      page-tree)))

(defn publish
  "Page might have children, which might have children; in other words; it might be a tree. All
  pages in the tree, if any, will be upserted.
  The root page must have an id.
  Returns a (flat) sequence of results. Each result will be either a representation of the remote
  page or an ::anom/anomaly."
  ([{:keys [::confluence/page-id ::md/children] :as _root-page} client]
   {:pre [page-id]}
   (doall (mapcat #(publish % page-id client) children)))
  ([{:keys [::confluence/page-id ::confluence/title ::md/children] :as page} parent-id client]
   {:pre [(nil? page-id)]}
   (let [result (upsert page parent-id client)
         page-id (get-in result [::confluence/page :id])
         succeeded? (boolean page-id)]
     (println (if (anom result)  "🚨" "✅") title)
     (if (and succeeded? (seq children))
       (doall (mapcat #(publish % page-id client) children))
       (cons result children)))))
