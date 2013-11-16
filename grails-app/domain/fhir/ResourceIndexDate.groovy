package fhir

class ResourceIndexDate extends ResourceIndexTerm{
	Date date_min
	Date date_max

	static mapping = {
		version false
	}
}

