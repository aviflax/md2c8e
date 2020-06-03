(ns md2c8e.cli
  (:require [cli-matic.core :refer [run-cmd]]
            [clojure.java.io :as io :refer [file]]
            [cognitect.anomalies :as anom]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.confluence :as c8e :refer [make-client]]
            [md2c8e.core :refer [dir->page-tree publish]]
            [md2c8e.links :refer [replace-links]]
            [md2c8e.markdown :as md]
            [md2c8e.paths :as paths]))

(defn- summarize
  [results source-dir]
  (let [{:keys [:created :updated :failed :skipped]}
        (group-by #(cond (anom %)              :failed
                         (::c8e/page %) (keyword (str (name (::c8e/operation %)) "d"))
                         :else                 :skipped)
                  results)]
    (println (format (str "-------------------\n"
                          "✅ Created: %s\n"
                          "✅ Updated: %s\n"
                          "⚠️ Skipped: %s\n"
                          "🔥 Failed: %s")
                     (count created)
                     (count updated)
                     (count skipped)
                     (count failed)))
    (doseq [{:keys [::c8e/page ::anom/message]} failed
            :let [sfrp ;; source-file-relative-path
                  (paths/relative-path source-dir (get-in page [::md/source ::md/fp]))]]
      (println "   🚨" (str sfrp) "\n"
               "    " message "\n"))))

(defn- publish-cmd
  [{:keys [source-dir root-page-id site-root-url username password]}]
  (let [client (make-client site-root-url username password)
        threads 10] ;; TODO: Make threads a command-line option
    (-> (dir->page-tree (file source-dir) root-page-id)
        (replace-links source-dir)
        ; (validate)
        (publish client threads)
        (summarize source-dir))))

(def config
  ;; The spec for this is here: https://github.com/l3nz/cli-matic/blob/master/README.md
  ;; :default :present means required ¯\_(ツ)_/¯
  {:app         {:command     "md2c8e"
                 :description "“Markdown to Confluence” — A tool for publishing sets of Markdown documents to Confluence"
                 :version     "TBD"}
   :commands    [{:command    "publish"
                  :description "Publish the specified docset to the specified Confluence site."
                  :opts [{:option  "source-dir"
                          :as      "The path to the Markdown docset to publish"
                          :type    :string
                          :default :present}
                         {:option  "root-page-id"
                          :as      "The ID of the page under which the docset should be published"
                          :type    :int
                          :default :present}
                         {:option  "site-root-url"
                          :as      "The root URL of the Confluence site to which the docset should be published"
                          :type    :string
                          :default :present}
                         {:option  "username"
                          :short   "u"
                          :type    :string
                          :default :present}
                         {:option  "password"
                          :short   "p"
                          :type    :string
                          :default :present}]
                  :runs publish-cmd}]})

(defn -main
  [& args]
  (run-cmd args config))
