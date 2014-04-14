(ns cmr.metadata-db.int-test.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

;;; Enpoints for services - change this for tcp-mon
(def port 3000)

(def concepts-url (str "http://localhost:" port "/concepts/"))

(def concept-id-url (str "http://localhost:" port "/concept-id/"))

(def reset-url (str "http://localhost:" port "/reset"))

(def providers-url (str "http://localhost:" port "/providers"))

;;; constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sample-provider-id "PROV1")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; concepts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concept
  "Creates a concept to be used for testing."
  []
  {:concept-type :collection
   :native-id "provider collection id"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn get-concept-id
  "Make a GET to retrieve the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str concept-id-url concept-type "/" provider-id "/" native-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body  "concept-id")
        errors (get body "errors")]
    {:status status :concept-id concept-id :error-messages errors}))

(defn get-concept-by-id-and-revision
  "Make a GET to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str concepts-url concept-id "/" revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))

(defn get-concept-by-id
  "Make a GET to retrieve a concept by concept-id."
  [concept-id]
  (let [response (client/get (str concepts-url concept-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))

(defn get-concepts
  "Make a POST to retrieve concepts by concept-id and revision."
  [tuples]
  (let [body {:concept-revisions tuples}]
    (let [response (client/post (str concepts-url "search")
                                {:body (cheshire/generate-string body)
                                 :body-encoding "UTF-8"
                                 :content-type :json
                                 :accept :json
                                 :throw-exceptions false})
          status (:status response)
          concepts (vec (cheshire/parse-string (:body response)))]
      {:status status :concepts concepts})))

(defn concepts-and-concept-id-revisions-equal?
  "Compare a vector of concepts returned by the API to a set of concept-id/revision-ids."
  [concepts concept-id-revision-ids]
  (if (not= (count concepts) (count concept-id-revision-ids))
    false
    (every? true?
            (map (fn [[con-id rev-id]]
                   (some (fn [concept]
                           (and (= (get concept "concept-id") con-id)
                                (= (get concept "revision-id" rev-id))))
                         concepts))
                 concept-id-revision-ids))))

(defn concepts-and-ids-equal?
  ;; TODO - this might need to be changed to be order independent
  "Compare a vector of concepts returned by the API to a set of concept-ids"
  [concepts concept-ids]
  (if (not= (count concepts) (count concept-ids))
    false
    (every? true? (map #(= (get %1 "concept-id") %2) concepts concept-ids))))

(defn save-concept
  "Make a POST request to save a concept with JSON encoding of the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [response (client/post concepts-url
                              {:body (cheshire/generate-string concept)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        concept-id (get body "concept-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :concept-id concept-id :error-messages error-messages}))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  [concept-id & revision-id]
  (let [revision-id (first revision-id)
        url (if revision-id
              (format "%s%s/%s" concepts-url concept-id revision-id)
              (format "%s%s" concepts-url concept-id))
        response (client/delete url
                                {:throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))

(defn force-delete-concept
  "Make a DELETE request to permanently remove a revison of a concept."
  [concept-id revision-id]
  (let [url (format "%sforce-delete/%s/%s" concepts-url concept-id revision-id)
        response (client/delete url
                                {:throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept]
  (let [concept-id (:concept-id concept)
        revision-id (:revision-id concept)
        stored-concept-and-status (get-concept-by-id-and-revision concept-id revision-id)
        stored-concept (:concept stored-concept-and-status)
        stored-concept-id (:concept-id stored-concept)]
    (is (= stored-concept-id concept-id))))

;;; providers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  "Make a POST request to save a provider with JSON encoding of the provider  Returns a map with
  status and a list of error messages."
  [provider-id]
  (let [response (client/post providers-url
                              {:body (cheshire/generate-string {:provider-id provider-id})
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        error-messages (get body "errors")
        provider-id (get body "provider-id")]
    {:status status :error-messages error-messages :provider-id provider-id}))

(defn get-providers
  "Make a GET request to retrieve the list of providers."
  []
  (let [response (client/get providers-url
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        error-messages (get body "errors")
        providers (get body "providers")]
    {:status status :error-messages error-messages :providers providers}))

(defn delete-provider
  "Make a DELETE request to remove a provider."
  [provider-id]
  (let [response (client/delete (format "%s/%s" providers-url provider-id)
                                {:accept :json
                                 :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        error-messages (get body "errors")]
    {:status status :error-messages error-messages}))


(defn verify-provider-was-saved
  "Verify that the given provider-id is in the list of providers."
  [provider-id]
  (some #{provider-id} (:providers (get-providers))))

;;; miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [response (client/post reset-url {:throw-exceptions false})
        status (:status response)]
    status))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reset-database-fixture
  "Reset the database after every test."
  [f]
  (try
    (f)
    (finally (reset-database))))