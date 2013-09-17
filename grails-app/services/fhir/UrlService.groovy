package fhir;



class UrlService{
	def transactional = false
	def grailsLinkGenerator

	private def fhirCombinedId(String p) {
		String ret = null
		def m = p =~ /\/([^\/]+)\/@([^\/]+)(?:\/history\/@([^\/]+))?/

		if (m.size()) {
			ret =  m[0][1] + '/@' + m[0][2]
		}

		return ret
	}

	String getBaseURI() {
		grailsLinkGenerator.link(uri:'', absolute:true)
	}

	String getDomain() {
		def m = baseURI =~ /(https?:\/\/[^\/]+)/
		return m[0][1]
	}

	String getFhirBase() {
		baseURI
	}

	URL getFhirBaseAbsolute() {
		new URL(fhirBase + '/')
	}

	String relativeResourceLink(String resource, String id) {
		"$resource/@$id"
	}

	String resourceLink(String resourceName, String fhirId) {
		grailsLinkGenerator.link(
				mapping: 'resourceInstance',
				absolute: true,
				params: [
					resource: resourceName,
					id: fhirId
				]).replace("%40","@")
	}

	String resourceVersionLink(String resourceName, String fhirId, String vid) {
		grailsLinkGenerator.link(
				mapping: 'resourceVersion',
				absolute: true,
				params: [
					resource: resourceName,
					id: fhirId,
					vid:vid 
				]).replace("%40","@")
	}
	
	String fullRequestUrl(request) {
		return domain + request.forwardURI + '?' + (request.queryString ?: "")
	}
}
