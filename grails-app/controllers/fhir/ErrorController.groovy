package fhir

class ErrorController {
	
	static scope = "singleton"

	private def status(int s) {
		log.debug("Rendering a $s error")
		def extra = ""

		if (request.exception) {
			log.debug(request.exception.stackTrace)
			extra = request.exception.message
		}
		response.status=s
		render("Request not authorized. $extra")
	}	
	
	def status401() {
		status(401)
	}	

	def status405() {
		status(405)
	}	

}
