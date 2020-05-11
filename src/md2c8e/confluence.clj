(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
            [md2c8e.anomalies :refer [anom fault]]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(def ^:private constants
  {:connect-timeout 5000
   :request-timeout 10000})

(defn- api-url
  [confluence-root-url first-path-segment & more-path-segments]
  {:pre [(str/ends-with? confluence-root-url "/")]}
  (str confluence-root-url
       "rest/api/"
       (->> (cons first-path-segment more-path-segments)
            (map #(if (keyword? %) (name %) (str %)))
            (str/join "/"))))

(defn- ensure-trailing-slash
  [s]
  (if (str/ends-with? s "/")
      s
      (str s "/")))

(defn make-client
  [confluence-root-url username password]
  {::confluence-root-url confluence-root-url
   ::url (partial api-url (ensure-trailing-slash confluence-root-url))
   ::req-opts {:http-client (hc/build-http-client {:connect-timeout (:connect-timeout constants)
                                                   :redirect-policy :never
                                                   :version :http-1.1
                                                   :cookie-policy :none})
               :accept :json
               :as :json
               :basic-auth {:user username, :pass password}
               :coerce :always
               :timeout (:request-timeout constants)
               :throw-exceptions? false}})

(defn- successful?
  [{status :status :as _response}]
  (and (number? status)
       (<= 200 status 206)))

(defn- response->fault
  "If the response represents an unsuccessful, error, or failed request, wraps it in an
  ::anom/anomaly and returns it. Otherwise returns nil."
  [res]
  (when-not (successful? res)
    (fault ::response res
           ::anom/message (get-in res [:body :message]))))

(defn- get-page-by-id
  "Returns the page with the supplied ID, or an ::anom/anomaly.
   The value of ::anom/category will vary depending on the circumstances:
    * :not-found — the page simply doesn’t exist
    * :fault — something went wrong"
  [page-id {:keys [::req-opts ::url] :as _client}]
  (let [res (hc/get (url :content page-id) (assoc req-opts :query-params {:expand "version,space"}))]
    (case (:status res)
      200 (:body res)
      404 {::anom/category :not-found
           ::anom/message (format "No page with ID %s exists" page-id)
           ::page-id page-id
           ::response res}
      (response->fault res))))

(defn page-exists?!
  "Returns nil if the page exists; throws an Exception if it does not."
  [page-id client]
  (when-let [res (anom (get-page-by-id page-id client))]
    (throw (ex-info "Page does not exist!" res))))

(defn- get-page-by-title
  "Get the page with the supplied title, or nil if no such page is found."
  [title space-key {:keys [::req-opts ::url] :as _client}]
  (let [res (hc/get (url :content) (assoc req-opts :query-params {:spaceKey space-key
                                                                  :title title
                                                                  :expand "version"}))
        results (get-in res [:body :results])]
    (if (and (= (:status res) 200)
             (<= (count results) 1))
      (first results) ; will either return the page, if present, or nil
      (response->fault res))))

(defonce ^{:private true, :doc "Atom containing a map of page IDs to space keys, for caching."}
  page-id->space-key
  (atom {}))

(defn- get-page-space-key
  [page-id client]
  (or (get @page-id->space-key page-id)
      (let [res (get-page-by-id page-id client)
            key (get-in res [:space :key])]
        (or (anom res)
            (swap! page-id->space-key assoc page-id key)))))

(defn- exception->fault
  [e]
  (merge (ex-data e)
         (fault ::anom/message (str e)
                :exception e)))

(defn- update-page
  [{id           :id
    {vn :number} :version :as _current-page}
   title
   body
   {:keys [::req-opts ::url] :as _client}]
  {:pre [(number? vn)]}
  (let [form-params {:version {:number (inc vn)}
                     :type :page
                     :title title
                     :body {:storage {:value body, :representation :storage}}}
        res (try (hc/put (url :content id)
                         (assoc req-opts :content-type :json, :form-params form-params))
                 (catch Exception e (exception->fault e)))]
    (or (anom res)
        (response->fault res)
        (:body res))))

(defn- create-page
  [space-key parent-id title body {:keys [::req-opts ::url] :as _client}]
  (let [form-params {:type :page
                     :title title
                     :space {:key space-key}
                     :body {:storage {:value body, :representation :storage}}
                     :ancestors [{:type :page :id parent-id}]}
        res (try (hc/post (url :content)
                          (assoc req-opts :content-type :json, :form-params form-params))
                 (catch Exception e (exception->fault e)))]
     (or (anom res)
         (response->fault res)
         (:body res))))

(defn- enrich-err
  [err operation source-file title body]
  (let [tmpfile (File/createTempFile (or (and source-file (.getName source-file))
                                         title)
                                     ".xhtml")]
    (spit tmpfile body)
    (-> (assoc err ::operation operation)
        (update ::anom/message #(str % " --  XHTML body written to: " tmpfile)))))

(defn upsert
  "Useful for when we want to publish a page that may or may not have already been published; we
  don’t know, and we don’t have an id for it.
  If successful, returns a map with [::operation ::page]. ::page is a representation of the page
  that was updated or created. Therefore it probably contains, among other keys, 'id' and 'version'.
  If unsuccessful, returns an ::anom/anomaly with the additional key ::response."
  [{:keys [::title ::body ::md/source] :as _page} parent-id client]
  (let [space-key-res (get-page-space-key parent-id client)
        space-key     (when-not (anom space-key-res)
                        space-key-res)
        get-res       (when space-key
                        (get-page-by-title title space-key client))
        page          (when-not (anom get-res)
                        get-res)
        op (cond (or (anom space-key-res) (anom get-res)) :none
                 page                                     :update
                 :else                                    :create)
        op-res (case op
                     :update (update-page page title body client)
                     :create (create-page space-key parent-id title body client)
                     :none)
        err (or (anom space-key-res)
                (anom get-res)
                (anom op-res))]
    (if err
      (enrich-err err op (::md/fp source) title body)
      {::operation op
       ::page op-res
       ::versions {::prior (get-in page [:version :number])
                   ::new (get-in op-res [:version :number])}})))
