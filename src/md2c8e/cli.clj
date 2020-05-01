(ns md2c8e.cli
  (:require [clojure.java.io :as io :refer [file]]
            [cognitect.anomalies :as anom]
            [md2c8e.anomalies :refer [anom?]]
            [md2c8e.confluence :as confluence :refer [make-client page-exists?!]]
            [md2c8e.core :refer [dir->page-tree replace-links publish]]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths]))

(defn- summarize
  [ptap source-dir] ; ptap == page-tree-after-publish
  (let [{:keys [:failed :succeeded :skipped]}
        (group-by #(cond (anom? %)    :failed
                         (get % "id") :succeeded ; the results of publish have string keys
                         :else        :skipped)
                  ptap)]
    (println (format (str "-------------------\n"
                          "✅ Succeeded: %s\n"
                          "🔥 Failed: %s\n"
                          "⚠️ Skipped: %s")
                     (count succeeded)
                     (count failed)
                     (count skipped)))
    (doseq [{:keys [::confluence/page ::anom/message]} failed
            :let [sfrp (paths/relative-path source-dir (get-in page [::md/source ::md/fp]))]] ;; sfrp == source-file-relative-path
      (println "   🚨" (str sfrp) "\n"
               "    " message "\n"))))

(defn -main
  [& [source-dir
      root-page-id
      api-root-url
      username
      password :as _args]]
  (let [client (make-client api-root-url username password)
        _ (page-exists?! root-page-id client)]
    (-> (dir->page-tree (file source-dir) root-page-id)
        (replace-links source-dir)
        ; (validate)
        (publish client)
        (summarize source-dir))))


(comment
  (def source-dir "CHANGEME")
  (def root-page-id "CHANGEME")
  (def api-root-url "CHANGEME")
  (def username "CHANGEME")
  (def password "CHANGEME")

  (-main source-dir root-page-id api-root-url username password))
