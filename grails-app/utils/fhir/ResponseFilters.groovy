package fhir
import org.hl7.fhir.instance.model.Binary

class ResponseFilters {
	def filters = {
		renderContent(controller: '*', action: '*') {
			after = {
				
				// TODO figure out why response.format is pinned to "all"
				// (and after unpinning it, clean up the logic below).
				def acceptable = request.getHeaders('accept')*.toLowerCase() + request._format
				
				def r = request?.resourceToRender
				if (!r) {return true}

				if (params.raw?.toBoolean()) {

					if (r.class != Binary) {
						response.status = 406
						render(text: "Can only request Raw for binary resources, not " + r.class.toString())
						return false
					}
					response.contentType = r.contentType
					response.outputStream << r.content
					response.outputStream.flush()							
					return false
				}
	
				if ("json" in acceptable || "text/json" in acceptable)
					render(text: r.encodeAsFhirJson(), contentType:"text/json")
				else
					render(text: r.encodeAsFhirXml(), contentType:"text/xml")
				return false
				
			}			
		}
	}
}