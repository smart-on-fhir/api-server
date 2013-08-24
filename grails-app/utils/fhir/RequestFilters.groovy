package fhir

class RequestFilters {
	def authorizationService
	def grailsApplication

	def filters = {
		if (grailsApplication.config.fhir.oauth.enabled)
		renderContent(controller: 'api', action: '*') {
			before = {

				def allowed = authorizationService.decide(request)
				log.debug("Allowed? : " + allowed)
				if (!allowed || !allowed.active) {
					response.status = 401
					render("Authorization failed.")
					return false
				}
				if(allowed) return true


			}			
		}
	}
}
