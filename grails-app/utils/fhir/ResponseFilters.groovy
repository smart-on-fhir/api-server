package fhir
import org.hl7.fhir.instance.model.AtomFeed;
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Resource

class ResponseFilters {
	def filters = {
		renderContent(controller: '*', action: '*') {
			after = {
				
				if (response.status == 204) return false

				// TODO figure out why response.format is pinned to "all"
				// (and after unpinning it, clean up the logic below).
				def acceptable = request.getHeaders('accept')*.toLowerCase() + params._format

				def r = request?.resourceToRender
				if (!r) {return true}

				if (!(r instanceof Resource || r instanceof AtomFeed)) {
					log.debug("Got a " + r)
					r = r.content.toString().decodeFhirJson()
				}

				if (r.class == Binary && !params.noraw) {
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