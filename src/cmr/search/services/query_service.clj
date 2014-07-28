(ns cmr.search.services.query-service
  "Defines operations for querying for concepts.

  Steps to process a query:

  - Validate parameters
  - Convert parameters into query model
  - Query validation
  - Apply ACLs to query
  - Query simplification
  - Convert Query into Elastic Query Model
  - Send query to Elasticsearch
  - Convert query results into requested format"
  (:require [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.models.query :as qm]

            ;; parameter-converters
            ;; These must be required here to make multimethod implementations available.
            [cmr.search.services.parameters.conversion :as p]
            [cmr.search.services.parameters.converters.collection-query]
            [cmr.search.services.parameters.converters.temporal]
            [cmr.search.services.parameters.converters.attribute]
            [cmr.search.services.parameters.converters.orbit-number]
            [cmr.search.services.parameters.converters.equator-crossing-longitude]
            [cmr.search.services.parameters.converters.equator-crossing-date]
            [cmr.search.services.parameters.converters.spatial]
            [cmr.search.services.parameters.converters.science-keyword]

            ;; Validation
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.validators.date-range]
            [cmr.search.validators.attribute]
            [cmr.search.validators.numeric-range]
            [cmr.search.validators.orbit-number]
            [cmr.search.validators.equator-crossing-longitude]
            [cmr.search.validators.equator-crossing-date]

            ;; Complex to simple converters
            [cmr.search.data.complex-to-simple-converters.attribute]
            [cmr.search.data.complex-to-simple-converters.orbit]
            [cmr.search.data.complex-to-simple-converters.temporal]
            [cmr.search.data.complex-to-simple-converters.spatial]

            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.search.services.collection-query-resolver :as r]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.services.provider-holdings :as ph]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.services.transformer :as t]
            [cmr.metadata-db.services.concept-service :as meta-db]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as err]
            [cmr.common.util :as u]
            [cmr.common.cache :as cache]
            [camel-snake-kebab :as csk]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]))

(deftracefn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  (let [errors (v/validate query)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors))
    query))

(deftracefn resolve-collection-query
  "Replace the collection query conditions in the query with conditions of collection-concept-ids."
  [context query]
  (r/resolve-collection-query query context))

(defmulti search-results->response
  "Converts query search results into a string response."
  (fn [context query results]
    (:result-format query)))

(defmulti search-results->response-result
  "Converts query search results into a json response.
  This is added so that we can directly call get-collections-by-providers within this namespace."
  (fn [context query results]
    (:result-format query)))

(defn- sanitize-params
  "Manipulates the parameters to make them easier to process"
  [params]
  (-> params
      u/map-keys->kebab-case
      (update-in [:options] u/map-keys->kebab-case)
      (update-in [:options] #(when % (into {} (map (fn [[k v]]
                                                     [k (u/map-keys->kebab-case v)])
                                                   %))))
      (update-in [:sort-key] #(when % (if (sequential? %)
                                        (map csk/->kebab-case % )
                                        (csk/->kebab-case %))))))

(deftracefn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id."
  [context concept-type params]
  (let [[query-creation-time query] (u/time-execution
                                      (->> params
                                           sanitize-params
                                           ;; handle legacy parameters
                                           lp/replace-parameter-aliases
                                           (lp/process-legacy-multi-params-conditions concept-type)
                                           (lp/replace-science-keywords-or-option concept-type)

                                           (pv/validate-parameters concept-type)
                                           (p/parameters->query concept-type)
                                           (validate-query context)
                                           c2s/reduce-query))
        [query-execution-time results] (u/time-execution
                                         (->> query
                                              (resolve-collection-query context)
                                              (qe/execute-query context)))
        [result-gen-time result-str] (u/time-execution
                                       (search-results->response
                                         context query (assoc results :took (+ query-creation-time
                                                                               query-execution-time))))
        total-took (+ query-creation-time query-execution-time result-gen-time)]
    (debug "query-creation-time:" query-creation-time
           "query-execution-time:" query-execution-time
           "result-gen-time:" result-gen-time)
    (info (format "Found %d %ss in %d ms in format %s with params %s."
                  (:hits results) (name concept-type) total-took (:result-format query) (pr-str params)))
    result-str))

(deftracefn find-concept-by-id
  "Executes a search to metadata-db and returns the concept with the given cmr-concept-id."
  [context concept-id]
  (let [mdb-context (t/context->metadata-db-context context)
        concept (meta-db/get-concept mdb-context concept-id nil)]
    (when (:deleted concept)
      (err/throw-service-error
        :not-found
        (format "Concept with concept-id: %s has been deleted" concept-id)))
    concept))

(deftracefn reset
  "Clear the cache for search app"
  [context]
  (cache/reset-cache (-> context :system :cache)))

(deftracefn get-collections-by-providers
  "Returns all collections found by the given provider ids"
  [context provider-ids]
  (let [query-condition (if (empty? provider-ids) (qm/map->MatchAllCondition {})
                          (qm/string-conditions :provider-id provider-ids))
        query (qm/query {:concept-type :collection
                         :condition query-condition
                         :page-size :unlimited
                         :result-format :json})
        results (->> query
                     (resolve-collection-query context)
                     (qe/execute-query context))
        all-collections (-> (search-results->response-result context query results)
                            (get-in [:feed :entry]))]
    (map #(select-keys % [:id :data_center :title]) all-collections)))

(deftracefn get-provider-holdings
  "Executes elasticsearch search to get provider holdings"
  [context params]
  (let [{provider-ids :provider-id legacy-provider-ids :provider_id pretty? :pretty} params
        provider-ids (or provider-ids legacy-provider-ids)
        ;; make sure provider-ids is sequential
        provider-ids (if (or (nil? provider-ids) (sequential? provider-ids))
                       provider-ids
                       [provider-ids])
        ;; get all collections limited by the list of providers in json format
        collections (get-collections-by-providers context provider-ids)
        ;; get a mapping of collection to granule count
        collection-granule-count (idx/get-collection-granule-counts context provider-ids)
        ;; combine the granule count into collections to form provider holdings
        provider-holdings (map #(assoc % :granule-count (get collection-granule-count (:id %)))
                               collections)]

    (ph/provider-holdings->string (:result-format params) provider-holdings pretty?)))
