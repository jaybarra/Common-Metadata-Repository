(ns cmr.search.services.provider-holdings
  "Defines functions to generate xml and json string for provider-holdings"
  (:require [clojure.set :as set]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cheshire.core :as json]))

(defmulti provider-holdings->string
  "Returns the string representation of the given provider-holdings"
  (fn [result-format provider-holdings pretty?]
  result-format))

(defn- provider-holding->xml-elem
  "Returns the XML element for the given provider holding"
  [provider-holding]
  (let [{:keys [id title data_center granule-count]} provider-holding]
    (x/element :provider-holding {}
               (x/element :entry-title {} title)
               (x/element :concept-id {} id)
               (x/element :granule-count {} granule-count)
               (x/element :provider-id {} data_center))))

(defmethod provider-holdings->string :xml
  [result-format provider-holdings pretty?]
  (let [xml-fn (if pretty? x/indent-str x/emit-str)]
    (xml-fn
      (x/element :provider-holdings {:type "array"}
                 (map provider-holding->xml-elem provider-holdings)))))

(defmethod provider-holdings->string :json
  [result-format provider-holdings pretty?]
  (let [holdings-json (map #(set/rename-keys % {:id :concept-id
                                                :title :entry-title
                                                :data_center :provider-id})
                           provider-holdings)]
    (json/generate-string holdings-json {:pretty pretty?})))