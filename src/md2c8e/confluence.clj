(ns md2c8e.confluence
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(defn- http-opts
  [username password]
  {:as :json ; coerce response bodies to Clojure data structures (maps/sequences) with Cheshire
   :basic-auth [username password]
   :connection-timeout 1000
   :cookie-policy :none ; ignore cookies in responses
   :decode-cookies false  ; ignore cookies in responses
   :redirect-strategy :none ; no thanks
   :socket-timeout 1000
   :unexceptional-status #{200 204}}) ; consider redirects exceptional

(defn make-client
  [confluence-root-url username password]
  {::confluence-root-url confluence-root-url
   ::username username
   ::password password
   ::opts (http-opts username password)})

(defn- url
  [confluence-root first-path-segment & more-path-segments]
  (apply str (concat [confluence-root
                      (when-not (str/ends-with? confluence-root "/")
                        "/")
                      "rest/api/"
                      (name first-path-segment)]
                     (map (comp name str) more-path-segments))))

(defn page-exists?!
  [page-id {:keys [::confluence-root-url ::opts] :as _client}]
  (let [res (http/get (url confluence-root-url :content page-id) opts)]
    (when-not (and (map? res)
                   (= (get-in res [:body :id]) (str page-id))
                   (= (get-in res [:body :type]) "page"))
      (throw (ex-info "Page does not exist!" {:page-id page-id :response res})))))

(defn upsert
  "If successful, returns the API response, which should include the String key 'id'.
  If unsuccessful, returns an anomaly with the additional key ::response"
  [page parent-id client]
  (let [py-res (py. client :update_or_create parent-id (::title page) (::body page))
        res (py/->jvm py-res)]
    (if (contains? res "id")
        res
        (let [tmpfile (File/createTempFile (.getName (get-in page [::md/source ::md/fp])) ".xhtml")]
          (spit tmpfile (::body page))
          {::anom/category :fault
           ::anom/message (str (or (get res "message")
                                   "API request failed.")
                               "\n  XHTML body written to: "
                               tmpfile)
           ::response res
           ::page page}))))
