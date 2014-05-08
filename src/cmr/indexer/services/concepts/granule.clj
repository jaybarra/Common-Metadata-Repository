(ns cmr.indexer.services.concepts.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]
            [cmr.umm.echo10.related-url :as ru]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.indexer.services.concepts.temporal :as temporal]
            [cmr.indexer.services.concepts.attribute :as attrib]
            [cmr.indexer.services.concepts.orbit-calculated-spatial-domain :as ocsd]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defn- get-parent-collection
  [context parent-collection-id]
  (let [concept (mdb/get-latest-concept context parent-collection-id)]
    (assoc (idx/parse-concept concept) :concept-id parent-collection-id)))

(defmethod idx/concept->elastic-doc :granule
  [context concept umm-granule]
  (let [{:keys [concept-id extra-fields provider-id revision-date]} concept
        {:keys [parent-collection-id]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [granule-ur data-granule temporal project-refs related-urls]} umm-granule
        producer-gran-id (:producer-gran-id data-granule)
        start-date (temporal/start-date :granule temporal)
        end-date (temporal/end-date :granule temporal)
        downloadable (not (empty? (filter ru/downloadable-url? related-urls)))]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id

     :entry-title.lowercase (s/lower-case (:entry-title parent-collection))
     :short-name.lowercase (s/lower-case (get-in parent-collection [:product :short-name]))
     :version-id.lowercase (s/lower-case (get-in parent-collection [:product :version-id]))

     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)
     :producer-gran-id producer-gran-id
     :producer-gran-id.lowercase (when producer-gran-id (s/lower-case producer-gran-id))

     ;; Provides sorting on a combination of producer granule id and granule ur
     :readable-granule-name-sort (s/lower-case (or producer-gran-id granule-ur))

     :project-refs project-refs
     :project-refs.lowercase (map s/lower-case project-refs)
     :orbit-calculated-spatial-domains (ocsd/ocsds->elastic-docs umm-granule)
     :attributes (attrib/psa-refs->elastic-docs parent-collection umm-granule)
     :revision-date revision-date
     :downloadable downloadable
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))}))
