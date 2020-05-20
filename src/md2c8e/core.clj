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
   ::md/children (->> (pmap file->page (io/page-files source-dir)) ; using mapv because file->page does IO
                      (mapv integrate-readme))})

(defn validate
  "Returns a sequence of errors. For example, if two pages have the same title, the sequence includes an
  anom."
  [_page-tree]
  (println "TODO: implement validate!!!")
  [{::anom/type :fault
    ::anom/message "TODO: implement validate!!!"}])

(defn- print-progress
  [page res]
  (println (str (if (anom res)  "🚨 " "✅ ")
                (::c8e/title page)
                (when-let [op (::c8e/operation res)]
                  (str " (" (name op) ")")))))

(defn publish
  "Page might have children, which might have children; in other words; it might be a tree. All
  pages in the tree, if any, will be upserted.
  The root page must have an id.
  Returns a (flat) sequence of results. Each result will be either a representation of the remote
  page or an ::anom/anomaly."

  ([{:keys [::c8e/page-id ::md/children] :as _root-page} client threads]
   {:pre [(some? page-id)]}
   (cp/with-shutdown! [pool (cp/threadpool threads)]
     (mapcat #(publish % page-id client pool) children)))

  ([{:keys [::md/children] :as page} parent-id client pool]
   (let [res (upsert page parent-id client)
         page-id (get-in res [::c8e/page :id])
         succeeded? (some? page-id)]
     (print-progress page res)
     (if (and succeeded? (seq children))
       (->> (mapv #(cp/future pool (publish % page-id client pool)) children)
            (mapv deref)
            (apply concat)
            (cons res))
       [res]))))
