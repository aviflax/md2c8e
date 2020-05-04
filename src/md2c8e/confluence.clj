(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [clj-http.client :as http]
            [martian.core :as m]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(defn- api-root-url
  [confluence-root-url]
  (str confluence-root-url
       (when (not (str/ends-with? api-root-url "/"))
         "/")
       "rest/api/"))

(def handlers
  [{:route-name :content
    :method :get
    :path-parts ["content" :id]
    :path-schema {:id s/Int}
    :produces ["application/json"]}])

(defn- auth-header-interceptor
  [username password]
  {:name ::add-authentication-header
   :enter (fn [ctx]
            (assoc-in ctx
                      [:request :headers "Authorization"]
                      (http/basic-auth-value [username password])))})

(defn make-client
  [confluence-root-url username password]
  (m/bootstrap (api-root-url confluence-root-url)
               handlers
               {:interceptors (concat m/default-interceptors
                                      [(auth-header-interceptor username password)])}))

(defn page-exists?!
  [page-id client]
  (let [res (m/response-for client :content {:id page-id})]
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


(comment
  
  (page-exists?! 59180266 (make-client "https://confluence.fundingcircle.com/" "architecture_team_automations" "BaVh0gEvywEw5O73l") )
  
  )
