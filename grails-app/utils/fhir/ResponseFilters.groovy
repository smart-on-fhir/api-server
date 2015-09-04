package fhir
import org.hibernate.SessionFactory
import org.hl7.fhir.instance.model.Bundle;
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Resource

class ResponseFilters {
  SessionFactory sessionFactory

  def filters = {
    renderContent(controller: '*', action: '*') {
      after = {

        sessionFactory.currentSession.flush()
        sessionFactory.currentSession.clear()

        if (response.status == 204) return false

        def r = request?.resourceToRender
        if (!r) {
          return true
        }

        if (!(r instanceof Resource || r instanceof Bundle)) {
          log.debug("Got a " + r)
          r = r.content.toString().decodeFhirJson()
        }

        if (r.class == Binary && !params.noraw) {
          response.contentType = r.contentType
          response.outputStream << r.content
          response.outputStream.flush()
          return false
        }

        if (request.acceptableFormat == "json")
          render(text: r.encodeAsFhirJson(), contentType:"application/json+fhir")
        else
          render(text: r.encodeAsFhirXml(), contentType:"application/xml+fhir")

        if (request?.t0)
          log.debug("rendered after: " + (new Date().getTime() - request.t0))

        return false
      }
    }
  }
}