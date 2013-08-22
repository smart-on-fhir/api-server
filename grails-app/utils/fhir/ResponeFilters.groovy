package fhir

class ResponeFilters {
	def filters = {
		renderContent(controller: '*', action: '*') {
			after = {
				def r = request?.resourceToRender
				if (!r) {return true}
				request.withFormat {
					xml {
						render(text: r.encodeAsFhirXml(), contentType:"text/xml")
					} json {
						render(text: r.encodeAsFhirJson(), contentType:"text/json")
					}
				}
				log.debug("Rendered: "  + new Date().getTime() + "")
				return false
			}
			
		}
	}
}