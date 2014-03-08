package fhir

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.model.Resource
import org.springframework.util.xml.SimpleNamespaceContext
import org.xml.sax.InputSource


class XmlService {

  static def transactional = false
  static def lazyInit = false

  static SimpleNamespaceContext nsContext
  GrailsApplication grailsApplication
  XPath xpathEvaluator = XPathFactory.newInstance().newXPath()

  private configureXpathSettings() {
    nsContext = new SimpleNamespaceContext();

    grailsApplication.config.fhir.namespaces.each { prefix, uri ->
      nsContext.bindNamespaceUri(prefix, uri)
    }
    xpathEvaluator.setNamespaceContext(nsContext)
  }

  public org.w3c.dom.Document fromResource(Resource r) throws IOException, Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    org.w3c.dom.Document d = builder.parse(new InputSource(new StringReader(r.encodeAsFhirXml())));
    return d;
  }

}
