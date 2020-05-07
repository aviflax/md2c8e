(ns md2c8e.core
  (:require [clojure.string :as str :refer [ends-with? lower-case]]
            [cognitect.anomalies :as anom]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.confluence :as confluence :refer [upsert]]
            [md2c8e.io :as io]
            [md2c8e.markdown :as md]
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

(defn- integrate-readme
  "Looks for a child page with the filename README.md (case-insensitive). If found, copies its
  title and content to the parent, then removes that child from the sequence of children."
  [page]
  (if-let [readme (find-first readme? (::md/children page))]
    (-> (update page ::md/children (fn [children]
                                     (remove readme? children)))
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
                      (mapv integrate-readme))})

(defn validate
  "Returns a sequence of errors. For example, if two pages have the same title, the sequence includes an
  anom."
  [page-tree]
  (println "TODO: implement validate!!!")
  [{::anom/type :fault
    ::anom/message "TODO: implement validate!!!"}])

(defn publish
  "Page might have children, which might have children; in other words; it might be a tree. All
  pages in the tree, if any, will be upserted.
  The root page must have an id.
  Returns a (flat) sequence of results. Each result will be either a representation of the remote
  page or an ::anom/anomaly."

  ([{:keys [::confluence/page-id ::md/children] :as _root-page} client]
   {:pre [page-id]}
   (-> (mapcat #(publish % page-id client) children)
       (doall)))

  ([{:keys [::confluence/page-id ::confluence/title ::md/children] :as page} parent-id client]
   {:pre [(nil? page-id)]}
   (let [result (upsert page parent-id client)
         page-id (get-in result [::confluence/page :id])
         succeeded? (some? page-id)]
     (println (if (anom result)  "🚨" "✅") title (when-let [op (::confluence/operation result)]
                                                     (str "(" (name op) ")")))
     (if (and succeeded? (seq children))
       (doall (mapcat #(publish % page-id client) children))
       [result]))))
