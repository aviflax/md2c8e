(ns md2c8e.anomalies
  (:require [cognitect.anomalies :as anom]))

(defn anom?
  [v]
  (::anom/category v))
