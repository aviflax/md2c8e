(ns md2c8e.confluence
  (:require [cognitect.anomalies :as anom]
            [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :refer [py.] :as py]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(require-python 'atlassian)

(defn make-client
  [api-root-url username password]
  (atlassian/Confluence api-root-url username password))

(defn page-exists?!
  [page-id client]
  (let [res (py. client :get_page_by_id page-id)]
    (when-not (and (= (type res) :pyobject)
                   (get res "id"))
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
