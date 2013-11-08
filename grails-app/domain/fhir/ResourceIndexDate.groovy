package fhir

class ResourceIndexDate extends ResourceIndexTerm{
	String search_param
	Date date_min
	Date date_max

	static mapping = {
		version false
	}
}

