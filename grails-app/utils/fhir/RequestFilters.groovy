package fhir

class RequestFilters {
	def authorizationService
	def grailsApplication

	def filters = {
		renderContent(controller: 'api', action: '*') {
			before = {
				if (!authorizationService.evaluate(request)) {
					forward controller: 'error', action: 'status401'
					return false
				}
				request.t0 = new Date().getTime()
				return true
			}			
		}
	}
}
