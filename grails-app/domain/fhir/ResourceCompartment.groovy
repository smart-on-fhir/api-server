package fhir

class ResourceCompartment {

	String fhir_id
	String fhir_type
	List<String> compartments

	static mapping = {
		table 'resource_compartment'
		version false
	}

}