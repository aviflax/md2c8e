(ns md2c8e.cli
  (:require [clojure.java.io :as io :refer [file]]
            [cognitect.anomalies :as anom]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.confluence :as confluence :refer [make-client page-exists?!]]
            [md2c8e.core :refer [dir->page-tree publish]]
            [md2c8e.links :refer [replace-links]]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths]))

(defn- summarize
  [ptap source-dir] ; ptap == page-tree-after-publish
  (let [{:keys [:created :updated :failed :skipped]}
        (group-by #(cond (anom %)              :failed
                         (::confluence/page %) (keyword (str (name (::confluence/operation %)) "d"))
                         :else                 :skipped)
                  ptap)]
    (println (format (str "-------------------\n"
                          "✅ Created: %s\n"
                          "✅ Updated: %s\n"
                          "⚠️ Skipped: %s\n"
                          "🔥 Failed: %s")
                     (count created)
                     (count updated)
                     (count skipped)
                     (count failed)))
    (doseq [{:keys [::confluence/page ::anom/message]} failed
            :let [sfrp ;; source-file-relative-path
                  (paths/relative-path source-dir (get-in page [::md/source ::md/fp]))]]
      (println "   🚨" (str sfrp) "\n"
               "    " message "\n"))))

(defn -main
  [& [source-dir
      root-page-id
      confluence-root-url ;; The root URL of the Confluence site, not the root of the REST API.
      username
      password :as _args]]
  (let [client (make-client confluence-root-url username password)
        _ (page-exists?! root-page-id client)]
    (-> (dir->page-tree (file source-dir) root-page-id)
        (replace-links source-dir)
        ; (validate)
        (publish client)
        (summarize source-dir))))


(comment
  (def source-dir "CHANGEME")
  (def root-page-id "CHANGEME")
  (def confluence-root-url "CHANGEME")
  (def username "CHANGEME")
  (def password "CHANGEME")

  (-main source-dir root-page-id confluence-root-url username password))
