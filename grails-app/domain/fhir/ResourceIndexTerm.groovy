package fhir

class ResourceIndexTerm {
	String fhir_id
	String fhir_type
	String search_param

	static mapping = {
        tablePerHierarchy false
		version false
	}
}