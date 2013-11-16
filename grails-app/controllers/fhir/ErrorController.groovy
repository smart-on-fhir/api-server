package fhir

class ErrorController {
	
	static scope = "singleton"

	private def status(int s) {
		log.debug("Rendering a $s error")
		def extra = ""
		if (request.exception) {
			extra = request.exception.message
		}
		response.status=s
		//render(extra)
		render("Failed" + extra)
	}	
	
	def status401() {
		status(401)
	}	

	def status405() {
		status(405)
	}

	def status500() {
		status(500)
	}	
	
	
	def deleted(){
		status(410)
	}

}
