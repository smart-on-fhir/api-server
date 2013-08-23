package fhir

class RequestFilters {
	def authorizationService
	
	def filters = {
		renderContent(controller: 'api', action: '*') {
			before = {
				authorizationService.decide(request)
			}			
		}
	}
}