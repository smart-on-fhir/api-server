package fhir

class ResponeFilters {
	def filters = {
		renderContent(controller: '*', action: '*') {
			after = {
				
				// TODO figure out why response.format is pinned to "all"
				// (and after unpinning it, clean up the logic below).
				def acceptable = request.getHeaders('accept')*.toLowerCase() + request._format
				
				def r = request?.resourceToRender
				if (!r) {return true}
				
				if ("json" in acceptable || "text/json" in acceptable)
					render(text: r.encodeAsFhirJson(), contentType:"text/json")
				else
					render(text: r.encodeAsFhirXml(), contentType:"text/xml")
				return false
				
			}			
		}
	}
}