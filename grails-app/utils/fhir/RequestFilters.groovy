package fhir

class RequestFilters {
  def authorizationService
  def grailsApplication

  def filters = {

    authorizeRequest(controllerExclude:'error', action: '*') {
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

    parseResourceBody(controllerExclude: 'error', action: '*') {
      before = {

        def providingFormat = request.getHeaders('content-type')*.toLowerCase() + params._format
        def acceptableFormat = request.getHeaders('accept')*.toLowerCase() + params._format

        if (acceptableFormat.any {it =~ /json/}) {
          request.acceptableFormat = "json"
        } else {
          request.acceptableFormat = "xml"
        }

        if (providingFormat.any {it =~ /json/}) {
          request.providingFormat = "json"
        } else {
          request.providingFormat = "xml"
        }
        println("Formats: ${request.acceptableFormat} + ${request.providingFormat}")
        return true
      }
    }
  }
}
