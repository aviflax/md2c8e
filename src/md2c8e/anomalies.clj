(ns md2c8e.anomalies
  (:require [cognitect.anomalies :as anom]))

(defn anom
  "If the value is a cognitect.anomalies/anomaly, returns the value; otherwise nil."
  [v]
  (and (::anom/category v) v))

(defn fault
  [first-key first-val & more-kvs]
  {:pre [(even? (count more-kvs))]}
  (apply hash-map
         (concat [::anom/category :fault, first-key first-val]
                 more-kvs)))
