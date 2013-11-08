package fhir

class ResourceIndexTerm {
	long version_id
	String fhir_id
	String fhir_type
	String search_param

	static mapping = {
		version false
	}
}