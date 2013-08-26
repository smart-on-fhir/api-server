package fhir
import org.hl7.fhir.instance.model.Binary

class ResponseFilters {
	def filters = {
		renderContent(controller: '*', action: '*') {
			after = {
				
				// TODO figure out why response.format is pinned to "all"
				// (and after unpinning it, clean up the logic below).
				def acceptable = request.getHeaders('accept')*.toLowerCase() + params._format
				
				def r = request?.resourceToRender
				if (!r) {return true}

				if (params.raw?.toBoolean()) {

					if (r.class != Binary) {
						response.status = 406
						log.debug("Got a request to render non-raw content: " + r)
						render(text: "Can only request Raw for binary resources, not " + r.class.toString())
						return false
					}
					response.contentType = r.contentType
					response.outputStream << r.content
					response.outputStream.flush()							
					return false
				}
	
				if (acceptable.any {it =~ /json/})
					render(text: r.encodeAsFhirJson(), contentType:"application/json")
				else
					render(text: r.encodeAsFhirXml(), contentType:"text/xml")

				if (request?.t0)	
				log.debug("rendered after: " + (new Date().getTime() - request.t0))	
				return false
				
			}			
		}
	}
}