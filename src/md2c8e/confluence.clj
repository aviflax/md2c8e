(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
            [md2c8e.anomalies :refer [anom]]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(def constants
  {:connect-timeout 1000
   :request-timeout 5000})

(defn- api-url
  [confluence-root-url first-path-segment & more-path-segments]
  {:pre [(str/ends-with? confluence-root-url "/")]}
  (str confluence-root-url
       "rest/api/"
       (->> (cons first-path-segment more-path-segments)
            (map #(if (keyword? %) (name %) (str %)))
            (str/join "/"))))

(defn make-client
  [confluence-root-url username password]
  {::confluence-root-url confluence-root-url
   ::url (partial api-url (str confluence-root-url (when-not (str/ends-with? confluence-root-url "/") "/")))
   ::req-opts {:http-client (hc/build-http-client {:connect-timeout (:connect-timeout constants)
                                                   :redirect-policy :never
                                                   :version :http-1.1
                                                   :cookie-policy :none})
               :accept :json
               :as :json-string-keys
               :basic-auth {:user username
                            :pass password}
               :timeout (:request-timeout constants)}})

(defn- get-page-by-id
  [page-id {:keys [::req-opts ::url] :as _client}]
  (let [res (hc/get (url :content page-id) req-opts)]
    (if (= (:status res) 200)
      (:body res)
      {::anom/category :fault
       ::response res})))

(defn page-exists?!
  [page-id client]
  (when-let [res (anom (get-page-by-id page-id client))]
    (throw (ex-info "Page does not exist!" {:page-id page-id
                                            :response (::response res)}))))

(defn- get-page-by-title
  [title space-key {:keys [::req-opts ::url] :as _client}]
  (let [res (hc/get (url :content)
                    (assoc req-opts :query-params {:spaceKey space-key :title title}))]
    (if (and (= (:status res) 200)
             (= (count (get-in res [:body "results"])) 1))
     (get-in res [:body "results" 0])
     {::anom/category :fault
      ::response res})))

; (defn- page-exists?
;   [title space-key client]
;   (let [res (get-page-by-title title space-key client)]
;     (not (anom res))))

(def ^:private get-page-space
  (memoize
    (fn [page-id client]
      (let [res (get-page-by-id page-id client)]
        (get-in res ["space" "key"] {::anom/category :fault ::response res})))))

(defn- update-page
  [id title body client])

(defn- create-page
  [space-key parent-id title body {:keys [::req-opts ::url] :as _client}]
  (hc/post (url :content)
           (assoc req-opts
                  :content-type :json
                  :form-params {:type :page
                                :title title
                                :space {:key space-key}
                                :body {:storage {:value body
                                                 :representation :storage}}
                                :ancestors [{:type :page :id parent-id}]})))

(defn upsert
  "Useful for when we want to publish a page that may or may not have already been published; we
  don’t know, and we don’t have an id for it.
  If successful, returns the API response, which should include the String key 'id'.
  If unsuccessful, returns an anomaly with the additional key ::response"
  [{:keys [::title ::body] :as _page} parent-id client]
  (let [space-key (get-page-space parent-id client)]
    (if-let [page (get-page-by-title title space-key client)]
      (update-page (get page "id") title body client)
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
  (def root-page-id 60489999)
  (def client (make-client confluence-root-url username password))
    
  (page-exists?! root-page-id client)
  
  (def root-page (get-page-by-id 60489999 client))
  
  (create-page (get-in root-page ["space" "key"])
               root-page-id
               "What about the cheese?"
               "The cheese is old and moldy, where is the bathroom?"
               client)
  )
