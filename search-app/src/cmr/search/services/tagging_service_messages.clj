(ns cmr.search.services.tagging-service-messages
  "This contains error response messages for the tagging service")

(def token-required-for-tag-modification
  "Tags cannot be modified without a valid user token.")

(defn tag-already-exists
  [tag concept-id]
  (format "A tag with namespace [%s] and value [%s] already exists with concept id %s."
          (:namespace tag) (:value tag) concept-id))

(def field-may-not-contain-separator
  "Validation format message so %s is included for the field"
  "%s may not contain the Group Separator character. ASCII decimal value: 29 Unicode: U+001D")

(defn cannot-change-field-value
  [existing-value new-value]
  (format "Tag %%s cannot be modified. Attempted to change existing value [%s] to [%s]"
          existing-value new-value))

(defn tag-does-not-exist
  [concept-id]
  (format "Tag could not be found with concept id [%s]" concept-id))

(defn bad-tag-concept-id
  [concept-id]
  (format "[%s] is not a valid tag concept id." concept-id))

(defn tag-deleted
  [concept-id]
  (format "Tag with concept id [%s] was deleted." concept-id))