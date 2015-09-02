package fhir
import org.hl7.fhir.instance.model.CodeableConcept
import org.hl7.fhir.instance.model.OperationOutcome

class ErrorController {

  static scope = "singleton"

  private def status(int s) {
    log.debug("Rendering a $s error")
    def extra = "Failed with error"
    if (request.exception) {
      extra = request.exception.message
    }
    response.status=s
    def o = new OperationOutcome()
    def i = o.addIssue()
      .setSeverity(OperationOutcome.IssueSeverity.ERROR)
      .setDetails(new CodeableConcept().setText(extra))
    request.resourceToRender = o
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
