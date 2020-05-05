(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
            [md2c8e.anomalies :refer [anom?]]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(def constants
  {:connect-timeout 1000
   :request-timeout 5000})

(defn make-client
  [confluence-root-url username password]
  {::confluence-root-url confluence-root-url
   ::req-opts {:http-client (hc/build-http-client {:connect-timeout (:connect-timeout constants)
                                                   :redirect-policy :never
                                                   :version :http-1.1
                                                   :cookie-policy :none})
               :accept :json
               :as :json-string-keys
               :basic-auth {:user username
                            :pass password}
               :timeout (:request-timeout constants)}})

(defn- url
  [confluence-root first-path-segment & more-path-segments]
  (str confluence-root
       (when-not (str/ends-with? confluence-root "/")
         "/")
       "rest/api/"
       (->> (cons first-path-segment more-path-segments)
            (map #(if (keyword? %) (name %) (str %)))
            (str/join "/"))))

(defn- get-page-by-id
  [page-id {:keys [::confluence-root-url ::req-opts] :as _client}]
  (let [res (hc/get (url confluence-root-url :content page-id) req-opts)]
    (if (= (:status res) 200)
      (:body res)
      {::anom/category :fault
       ::response res})))

(defn page-exists?!
  [page-id client]
  (let [res (get-page-by-id page-id client)]
    (when (anom? res)
      (throw (ex-info "Page does not exist!" {:page-id page-id
                                              :response (::response res)})))))

(defn- get-page-by-title
  [title space-key {:keys [::confluence-root-url ::req-opts] :as _client}]
  (let [res (hc/get (url confluence-root-url :content)
                    (assoc req-opts :query-params {:spaceKey space-key :title title}))]
    (if (and (= (:status res) 200)
             (= (count (get-in res [:body "results"])) 1))
     (get-in res [:body "results" 0])
     {::anom/category :fault
      ::response res})))

(defn- page-exists?
  [title space-key client]
  (let [res (get-page-by-title title space-key client)]
    (not (anom? res))))

(def ^:private get-page-space
  (memoize
    (fn [page-id client]
      (let [res (get-page-by-id page-id client)]
        (get-in res ["space" "key"] {::anom/category :fault ::response res})))))

(defn upsert
  "If successful, returns the API response, which should include the String key 'id'.
  If unsuccessful, returns an anomaly with the additional key ::response"
  [{:keys [::title ::body] :as _page} parent-id client]
  (let [space-key (get-page-space parent-id client)]
    (if-let [page (get-page-by-title? title space-key client)]
      (update-page (get page "id") etc)
      (create-page space-key parent-id title body client))))
    ; (if (contains? res "id")
    ;     res
    ;     (let [tmpfile (File/createTempFile (.getName (get-in page [::md/source ::md/fp])) ".xhtml")]
    ;       (spit tmpfile (::body page))
    ;       {::anom/category :fault
    ;        ::anom/message (str (or (get res "message")
    ;                                "API request failed.")
    ;                            "\n  XHTML body written to: "
    ;                            tmpfile)
    ;        ::response res
    ;        ::page page}))))

(comment
  (def confluence-root-url "CHANGEME")
  (def username "CHANGEME")
  (def password "CHANGEME")
  
  (page-exists?! 60489999 (make-client confluence-root-url username password)))
