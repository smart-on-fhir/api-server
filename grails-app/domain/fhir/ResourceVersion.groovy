package fhir

class ResourceVersion {

	long version_id
	String fhir_id
	String fhir_type

	Date rest_date = new Date()
	String rest_operation

	String content


	static mapping = {
		content type: 'text'
		table 'resource_version'
		id name: 'version_id'
		version false
	}

}