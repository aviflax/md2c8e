(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
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

(defn page-exists?!
  [page-id {:keys [::confluence-root-url ::req-opts] :as _client}]
  (let [res (hc/get (url confluence-root-url :content page-id) req-opts)]
    (when-not (and (map? res)
                   (= (get-in res [:body "id"]) (str page-id))
                   (= (get-in res [:body "type"]) "page"))
      (throw (ex-info "Page does not exist!" {:page-id page-id :response res})))))

; (defn upsert
;   "If successful, returns the API response, which should include the String key 'id'.
;   If unsuccessful, returns an anomaly with the additional key ::response"
;   [page parent-id client]
;   (let [py-res (py. client :update_or_create parent-id (::title page) (::body page))
;         res (py/->jvm py-res)]
;     (if (contains? res "id")
;         res
;         (let [tmpfile (File/createTempFile (.getName (get-in page [::md/source ::md/fp])) ".xhtml")]
;           (spit tmpfile (::body page))
;           {::anom/category :fault
;            ::anom/message (str (or (get res "message")
;                                    "API request failed.")
;                                "\n  XHTML body written to: "
;                                tmpfile)
;            ::response res
;            ::page page}))))

(comment
  (def confluence-root-url "CHANGEME")
  (def username "CHANGEME")
  (def password "CHANGEME")
  
  (page-exists?! 60489999 (make-client confluence-root-url username password)))
