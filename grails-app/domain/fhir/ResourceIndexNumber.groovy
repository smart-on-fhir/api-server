package fhir

class ResourceIndexNumber extends ResourceIndexTerm{
	String search_param
	float number_min
	float number_max

	static mapping = {
		version false
	}
}

