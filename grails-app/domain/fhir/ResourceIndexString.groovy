package fhir

class ResourceIndexString extends ResourceIndexTerm{
	String search_param
	String string_value

	static mapping = {
		string_value type: 'text'
        tablePerHierarchy false
		version false
	}
}

