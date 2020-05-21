(ns md2c8e.core
  (:require [clojure.string :as str :refer [ends-with? lower-case]]
            [cognitect.anomalies :as anom]
            [com.climate.claypoole :as cp]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.confluence :as c8e :refer [upsert]]
            [md2c8e.io :as io]
            [md2c8e.markdown :as md]
            [medley.core :as mc :refer [find-first]])
  (:import [java.io File]))

(defn- file->page
  [^File fp] ;; fp == file-pointer (can point to dirs too)
  (let [file-contents (when (.isFile fp) (slurp fp))
        title (md/file->page-title fp file-contents)]
    (println "Loading" title)
    {::c8e/page-id nil
     ::c8e/title title
     ::c8e/body (when (.isFile fp) (md/prep-content file-contents))
     ::md/source {::md/fp fp ;; fp == file-pointer (can point to dirs too)
                  ::md/contents file-contents
                  ::md/is-dir (.isDirectory fp)
                  ::md/is-file (.isFile fp)}
     ::md/children (if (.isDirectory fp)
                     (mapv file->page (io/page-files fp))
                     [])}))

(defn- readme?
  [{:keys [::md/source] :as _page}]
  (boolean (and (::md/is-file source)
                (ends-with? (lower-case (::md/fp source)) "readme.md"))))

(defn- integrate-readme
  "Looks for a child page with the filename README.md (case-insensitive). If found, copies its
  title and content to the parent, then removes that child from the sequence of children."
  [page]
  (if-let [readme (find-first readme? (::md/children page))]
    (-> (update page ::md/children (fn [children]
                                     (remove readme? children)))
        (merge (select-keys readme [::c8e/title ::c8e/body]))
        (assoc ::md/content-replaced-by readme))
    page))

(defn dir->page-tree
  "Returns a tree of pages as per file->page."
  [^File source-dir root-page-id]
  {:pre [(.isDirectory source-dir)]}
  {::c8e/page-id root-page-id
   ::md/children (->> (pmap file->page (io/page-files source-dir))
                      (mapv integrate-readme))}) ; using mapv because file->page does IO

(defn validate
  "Returns a sequence of errors. For example, if two pages have the same title, the sequence includes an
  anom."
  [_page-tree]
  (println "TODO: implement validate!!!")
  [{::anom/type :fault
    ::anom/message "TODO: implement validate!!!"}])

(defn- print-progress
  [{:keys [::c8e/title] :as _page} res]
  (println (str (if (anom res)  "🚨 " "✅ ")
                title
                (when-let [op (::c8e/operation res)]
                  (str " (" (name op) ")")))))

(defn- publish-child
 [{:keys [::c8e/page-id ::md/children] :as page} space-key parent-id client]
 {:pre [(nil? page-id)]}
 (let [res (upsert page space-key parent-id client)
       page-id (get-in res [::c8e/page :id])
       success? (some? page-id)]
   (print-progress page res)
   (if (and success? (seq children))
     (doall (mapcat #(publish-child % space-key page-id client) children))
     [res])))

(defn publish
  "NB: this employs a limited degree of concurrency: all subtrees from the root, in other words all
  direct children of the root, are published concurrently (with the degree of concurrency bounded to
  that specified by `threads`). This works well for trees that are wide but shallow. This may be
  sub-optimal for users whose document trees are deep, but this does work for my team for right now
  and that’s all I can do right now.
  
  The root page really should have children, because otherwise what’s the point? Those children
  might in turn have their own children, which might have children, etc; in other words; it might be
  a tree. All pages in the tree, except for the root, will be upserted.
  
  The root page must have an value for ::c8e/page-id.
  
  Returns a (flat) sequence of results. Each result will be either a representation of the remote
  page or an ::anom/anomaly."

  [{:keys [::c8e/page-id ::md/children] :as _root-page} client threads]
  {:pre [(some? page-id)]}
  (let [root-page-res (c8e/get-page-by-id page-id client)
        space-key (get-in root-page-res [:space :key])]
    (when (or (anom root-page-res)
              (not space-key))
      (throw (ex-info "Root page does not exist, or seems to be malformed." root-page-res)))
    (->> (cp/pmap threads #(publish-child % space-key page-id client) children)
         (doall)
         (mapcat identity))))
