(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.spatial :as spatial]
            [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.platform :as platform]
            [cmr.umm-spec.iso19115-2-util :as u]))

(def iso19115-2-xml-namespaces
  {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"})

(defn- date-mapping
  "Returns the date element mapping for the given name and date value in string format."
  [date-name value]
  [:gmd:date
   [:gmd:CI_Date
    [:gmd:date
     [:gco:DateTime value]]
    [:gmd:dateType
     [:gmd:CI_DateTypeCode {:codeList (str (:ngdc u/code-lists) "#CI_DateTypeCode")
                            :codeListValue date-name} date-name]]]])

(def attribute-data-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode")

(defn- generate-projects-keywords
  "Returns the content generator instructions for descriptive keywords of the given projects."
  [projects]
  (let [project-keywords (map u/generate-title projects)]
    (u/generate-descriptive-keywords "project" project-keywords)))

(defn- generate-projects
  [projects]
  (for [proj projects]
    (let [{short-name :ShortName} proj]
      [:gmi:operation
       [:gmi:MI_Operation
        [:gmi:description
         (char-string (u/generate-title proj))]
        [:gmi:identifier
         [:gmd:MD_Identifier
          [:gmd:code
           (char-string short-name)]]]
        [:gmi:status ""]
        [:gmi:parentOperation {:gco:nilReason "inapplicable"}]]])))

(defn- generate-distributions
  [distributions]
  (when-let [distributions (su/remove-empty-records distributions)]
    ;; We want to generate an empty element here because ISO distribution depends on
    ;; the order of elements to determine how the fields of a distribution are group together.
    (let [truncate-map (fn [key] (util/truncate-nils (map key distributions)))
          sizes (truncate-map :DistributionSize)
          fees (truncate-map :Fees)]
      [:gmd:distributionInfo
       [:gmd:MD_Distribution
        [:gmd:distributor
         [:gmd:MD_Distributor
          [:gmd:distributorContact {:gco:nilReason "missing"}]
          (for [fee (map su/nil-to-empty-string fees)]
            [:gmd:distributionOrderProcess
             [:gmd:MD_StandardOrderProcess
              [:gmd:fees
               (char-string fee)]]])
          (for [distribution distributions
                :let [{media :DistributionMedia format :DistributionFormat} distribution]]
            [:gmd:distributorFormat
             [:gmd:MD_Format
              [:gmd:name
               (char-string (su/nil-to-empty-string format))]
              [:gmd:version {:gco:nilReason "unknown"}]
              [:specification
               (char-string (su/nil-to-empty-string media))]]])
          (for [size (map su/nil-to-empty-string sizes)]
            [:gmd:distributorTransferOptions
             [:gmd:MD_DigitalTransferOptions
              [:gmd:transferSize
               [:gco:Real size]]]])]]]])))

(defn- generate-publication-references
  [pub-refs]
  (for [pub-ref pub-refs
        ;; Title and PublicationDate are required fields in ISO
        :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
    [:gmd:aggregationInfo
     [:gmd:MD_AggregateInformation
      [:gmd:aggregateDataSetName
       [:gmd:CI_Citation
        [:gmd:title (char-string (:Title pub-ref))]
        (when (:PublicationDate pub-ref)
          [:gmd:date
           [:gmd:CI_Date
            [:gmd:date
             [:gco:Date (second (re-matches #"(\d\d\d\d-\d\d-\d\d)T.*" (str (:PublicationDate pub-ref))))]]
            [:gmd:dateType
             [:gmd:CI_DateTypeCode
              {:codeList (str (:iso u/code-lists) "#CI_DateTypeCode")
               :codeListValue "publication"} "publication"]]]])
        [:gmd:edition (char-string (:Edition pub-ref))]
        (when (:DOI pub-ref)
          [:gmd:identifier
           [:gmd:MD_Identifier
            [:gmd:code (char-string (get-in pub-ref [:DOI :DOI]))]
            [:gmd:description (char-string "DOI")]]])
        [:gmd:citedResponsibleParty
         [:gmd:CI_ResponsibleParty
          [:gmd:organisationName (char-string (:Author pub-ref))]
          [:gmd:role
           [:gmd:CI_RoleCode
            {:codeList (str (:ngdc u/code-lists) "#CI_RoleCode")
             :codeListValue "author"} "author"]]]]
        [:gmd:citedResponsibleParty
         [:gmd:CI_ResponsibleParty
          [:gmd:organisationName (char-string (:Publisher pub-ref))]
          [:gmd:role
           [:gmd:CI_RoleCode
            {:codeList (str (:ngdc u/code-lists) "#CI_RoleCode")
             :codeListValue "publisher"} "publication"]]]]
        [:gmd:series
         [:gmd:CI_Series
          [:gmd:name (char-string (:Series pub-ref))]
          [:gmd:issueIdentification (char-string (:Issue pub-ref))]
          [:gmd:page (char-string (:Pages pub-ref))]]]
        [:gmd:otherCitationDetails (char-string (:OtherReferenceDetails pub-ref))]
        [:gmd:ISBN (char-string (:ISBN pub-ref))]]]
      [:gmd:associationType
       [:gmd:DS_AssociationTypeCode
        {:codeList (str (:ngdc u/code-lists) "#DS_AssociationTypeCode")
         :codeListValue "Input Collection"} "Input Collection"]]]]))

(defn extent-description-string
  "Returns the ISO extent description string (a \"key=value,key=value\" string) for the given UMM-C
  collection record."
  [c]
  (let [vsd (first (-> c :SpatialExtent :VerticalSpatialDomains))
        m {"VerticalSpatialDomainType" (:Type vsd)
           "VerticalSpatialDomainValue" (:Value vsd)
           "SpatialCoverageType" (-> c :SpatialExtent :SpatialCoverageType)
           "SpatialGranuleSpatialRepresentation" (-> c :SpatialExtent :GranuleSpatialRepresentation)}]
    (str/join "," (for [[k v] m
                        :when (some? v)]
                    (str k "=" (str/replace v #"[,=]" ""))))))

(defn umm-c-to-iso19115-2-xml
  "Returns the generated ISO19115-2 xml from UMM collection record c."
  [c]
  (let [platforms (platform/platforms-with-id (:Platforms c))]
    (xml
      [:gmi:MI_Metadata
       iso19115-2-xml-namespaces
       [:gmd:fileIdentifier (char-string (:EntryTitle c))]
       [:gmd:language (char-string "eng")]
       [:gmd:characterSet
        [:gmd:MD_CharacterSetCode {:codeList (str (:ngdc u/code-lists) "#MD_CharacterSetCode")
                                   :codeListValue "utf8"} "utf8"]]
       [:gmd:hierarchyLevel
        [:gmd:MD_ScopeCode {:codeList (str (:ngdc u/code-lists) "#MD_ScopeCode")
                            :codeListValue "series"} "series"]]
       [:gmd:contact {:gco:nilReason "missing"}]
       [:gmd:dateStamp
        [:gco:DateTime "2014-08-25T15:25:44.641-04:00"]]
       [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
       [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]
       (spatial/coordinate-system-element c)
       [:gmd:identificationInfo
        [:gmd:MD_DataIdentification
         [:gmd:citation
          [:gmd:CI_Citation
           [:gmd:title (char-string (:EntryTitle c))]
           (date-mapping "revision" "2000-12-31T19:00:00-05:00")
           (date-mapping "creation" "2000-12-31T19:00:00-05:00")
           [:gmd:identifier
            [:gmd:MD_Identifier
             [:gmd:code (char-string (:EntryId c))]
             [:gmd:version (char-string (:Version c))]]]]]
         [:gmd:abstract (char-string (:Abstract c))]
         [:gmd:purpose {:gco:nilReason "missing"} (char-string (:Purpose c))]
         [:gmd:status
          (when-let [collection-progress (:CollectionProgress c)]
            [:gmd:MD_ProgressCode
             {:codeList (str (:ngdc u/code-lists) "#MD_ProgressCode")
              :codeListValue (str/lower-case collection-progress)}
             collection-progress])]
         (generate-projects-keywords (:Projects c))
         (u/generate-descriptive-keywords "place" (:SpatialKeywords c))
         (u/generate-descriptive-keywords "temporal" (:TemporalKeywords c))
         (u/generate-descriptive-keywords (:AncillaryKeywords c))
         (platform/generate-platform-keywords platforms)
         (platform/generate-instrument-keywords platforms)
         [:gmd:resourceConstraints
          [:gmd:MD_LegalConstraints
           [:gmd:useLimitation (char-string (:UseConstraints c))]
           [:gmd:useLimitation
            [:gco:CharacterString (str "Restriction Comment:" (-> c :AccessConstraints :Description))]]
           [:gmd:otherConstraints
            [:gco:CharacterString (str "Restriction Flag:" (-> c :AccessConstraints :Value))]]]]
         (generate-publication-references (:PublicationReferences c))
         [:gmd:language (char-string (or (:DataLanguage c) "eng"))]
         [:gmd:extent
          [:gmd:EX_Extent {:id "boundingExtent"}
           [:gmd:description
            [:gco:CharacterString (extent-description-string c)]]
           (spatial/spatial-extent-elements c)
           (for [temporal (:TemporalExtents c)
                 rdt (:RangeDateTimes temporal)]
             [:gmd:temporalElement
              [:gmd:EX_TemporalExtent
               [:gmd:extent
                [:gml:TimePeriod {:gml:id (u/generate-id)}
                 [:gml:beginPosition (:BeginningDateTime rdt)]
                 [:gml:endPosition (su/nil-to-empty-string (:EndingDateTime rdt))]]]]])
           (for [temporal (:TemporalExtents c)
                 date (:SingleDateTimes temporal)]
             [:gmd:temporalElement
              [:gmd:EX_TemporalExtent
               [:gmd:extent
                [:gml:TimeInstant {:gml:id (u/generate-id)}
                 [:gml:timePosition date]]]]])]]
         [:gmd:processingLevel
          [:gmd:MD_Identifier
           [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
           [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
       [:gmd:contentInfo
        [:gmd:MD_ImageDescription
         [:gmd:attributeDescription ""]
         [:gmd:contentType ""]
         [:gmd:processingLevelCode
          [:gmd:MD_Identifier
           [:gmd:code (char-string (-> c :ProcessingLevel :Id))]
           [:gmd:description (char-string (-> c :ProcessingLevel :ProcessingLevelDescription))]]]]]
       (generate-distributions (:Distributions c))
       [:gmd:dataQualityInfo
        [:gmd:DQ_DataQuality
         [:gmd:scope
          [:gmd:DQ_Scope
           [:gmd:level
            [:gmd:MD_ScopeCode
             {:codeList (str (:ngdc u/code-lists) "#MD_ScopeCode")
              :codeListValue "series"}
             "series"]]]]
         [:gmd:report
          [:gmd:DQ_AccuracyOfATimeMeasurement
           [:gmd:measureIdentification
            [:gmd:MD_Identifier
             [:gmd:code
              (char-string "PrecisionOfSeconds")]]]
           [:gmd:result
            [:gmd:DQ_QuantitativeResult
             [:gmd:valueUnit ""]
             [:gmd:value
              [:gco:Record {:xsi:type "gco:Real_PropertyType"}
               [:gco:Real (:PrecisionOfSeconds (first (:TemporalExtents c)))]]]]]]]]]
       [:gmi:acquisitionInformation
        [:gmi:MI_AcquisitionInformation
         (platform/generate-instruments platforms)
         (generate-projects (:Projects c))
         (platform/generate-platforms platforms)]]])))

