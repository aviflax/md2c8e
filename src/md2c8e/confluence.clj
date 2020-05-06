(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
            [md2c8e.anomalies :refer [anom fault]]
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
  (let [res (hc/get (url :content page-id) (assoc req-opts :query-params {:expand "version"}))]
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
  "Get the page with the supplied title, or nil if no such page is found."
  [title space-key {:keys [::req-opts ::url] :as _client}]
  (let [res (hc/get (url :content)
                    (assoc req-opts :query-params {:spaceKey space-key
                                                   :title title
                                                   :expand "version"}))]
    (if (and (= (:status res) 200)
             (<= (count (get-in res [:body "results"])) 1))
      (get-in res [:body "results" 0]) ; will either return the page, if present, or nil
      (fault ::response res))))

(defonce ^{:private true, :doc "Atom containing a map of page IDs to space keys, for caching."}
  page-id->space-key
  (atom {}))

(defn- get-page-space
  [page-id client]
  (or (get @page-id->space-key page-id)
      (let [res (get-page-by-id page-id client)] ;; TODO: handle errors!
        (if-let [key (and (map? res)
                          (get-in res ["space" "key"]))]
          (swap! page-id->space-key assoc page-id key)
          (fault ::response res)))))

(defn- update-page
  [{id            "id"
    {vn "number"} "version" :as _current-page}
   title
   body
   {:keys [::req-opts ::url] :as _client}]
  {:pre [(number? vn)]}
  (hc/put
    (url :content id)
    (assoc req-opts
           :content-type :json
           :form-params {:version {:number (inc vn)}
                         :type :page
                         :title title
                         :body {:storage {:value body
                                          :representation :storage}}})))

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
  If successful, returns a map with [::operation ::page]. ::page is a representation of the page
  that was updated or created. Therefore it probably contains, among other keys, 'id' and 'version'.
  If unsuccessful, returns an anomaly with the additional key ::response"
  [{:keys [::title ::body ::md/source] :as _page} parent-id client]
  (let [space-key (get-page-space parent-id client)
        get-res (get-page-by-title title space-key client)]
    (or (anom get-res)
        (let [page get-res ; now we know it’s a page, or maybe nil — but definitely not an anomaly
              op (if page :update :create)
              op-res (case op
                           :update (update-page page title body client)
                           :create (create-page space-key parent-id title body client))]
          (if-let [err (anom get-res)]
            (let [tmpfile (File/createTempFile (or (and (::md/fp source)
                                                        (.getName (::md/fp source)))
                                                   title)
                                               ".xhtml")]
              (spit tmpfile (::body page))
              (update err ::anom/message #(str % "\n  XHTML body written to: " tmpfile)))
            {::operation op
             ::page (:body op-res)})))))

(comment
  (def confluence-root-url "CHANGEME")
  (def username            "CHANGEME")
  (def password            "CHANGEME")
  (def root-page-id 60489999)
  (def client (make-client confluence-root-url username password))

  (page-exists?! root-page-id client)
  
  (def root-page (get-page-by-id root-page-id client))
  
  @page-id->space-key
  (swap! page-id->space-key assoc "foo" "bar")
  
  (create-page (get-in root-page ["space" "key"])
               root-page-id
               "What about the cheese?"
               "The cheese is old and moldy, where is the bathroom?"
               client)
  
  (def cheese-page (get-page-by-id 60490891 client))
  
  (update-page cheese-page
               "What about what cheese?"
               "The cheese <b>really is</b> old and moldy, where is the bathroom?"
               client)

  (get-page-by-title "What about what cheese?" (get-in root-page ["space" "key"]) client)
  
  (time
  (upsert {::title "What about all that cheese?"
           ::body "The cheese is <i>very</i> old and moldy 🤢 …where is the bathroom?"
           ::md/source nil}
          root-page-id
          client))
  
  )
