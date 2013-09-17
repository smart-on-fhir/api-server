package fhir

class RequestFilters {
	def authorizationService
	def grailsApplication

	def filters = {
		authorizeRequest(controller: 'api', action: '*') {
			before = {

				if(params.action[request.method] in ['welcome', 'conformance']){
					return true
				}
					
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
