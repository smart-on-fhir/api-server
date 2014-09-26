package fhir
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.CodeableConcept
import org.hl7.fhir.instance.model.Coding
import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.DateAndTime
import org.hl7.fhir.instance.model.Extension
import org.hl7.fhir.instance.model.Profile
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.UriType
import org.hl7.fhir.instance.model.Conformance.ConformanceRestComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestOperationComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceOperationComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestSecurityComponent
import org.hl7.fhir.instance.model.Conformance.SystemRestfulOperation
import org.hl7.fhir.instance.model.Conformance.TypeRestfulOperation
import org.hl7.fhir.utilities.xhtml.NodeType
import org.hl7.fhir.utilities.xhtml.XhtmlNode

import com.google.common.collect.ImmutableMap

class ConformanceService {

  def grailsApplication
  static XmlService xmlService
  //static GrailsApplication grailsApplication
  static Conformance conformance
  static Map<String, String> searchParamXpaths
  static Map<String, List<String>> searchParamReferenceTypes
  static XmlParser parser = new XmlParser()
  static UrlService urlService
  Map oauth
  
  @PostConstruct
  void init() {
	oauth = grailsApplication.config.fhir.oauth
  }

  public static ClassLoader getClassLoader(){
    Thread.currentThread().contextClassLoader
  }

  private Resource resourceFromFile(String file) {
    def stream = classLoader.getResourceAsStream(file)
    parser.parse(stream)
  }

  def xpathToFhirPath(String xp){
    return xp.replace("/f:",".")[2..-1]
  }

  def generateConformance(){
    def xpathFixes = ImmutableMap.<String, String> builder()
    def xpathReferenceTypes = ImmutableMap.<String, List<String>> builder()

    Map spotFixes = grailsApplication.config.fhir.searchParam.spotFixes
    spotFixes.each { uri, xpath ->
      xpathFixes.put(uri, xpath)
    }

    Profile patient = resourceFromFile "resources/patient.profile.xml"

    println("Read patient profile" + patient)
    conformance = resourceFromFile "resources/conformance-base.xml"

    conformance.text.div = new XhtmlNode(NodeType.Element, "div");
    conformance.text.div.addText("Generated Conformance Statement -- see structured representation.")
    conformance.identifierSimple = urlService.fhirBase + '/conformance'
    conformance.publisherSimple = "SMART on FHIR"
    conformance.nameSimple =  "SMART on FHIR Conformance Statement"
    conformance.descriptionSimple = "Describes capabilities of this SMART on FHIR server"
    conformance.telecom[0].valueSimple = urlService.fhirBase

    conformance.dateSimple = DateAndTime.now()

	Extension registerUriExtension = new Extension()
	registerUriExtension.setUrlSimple("http://fhir-registry.smartplatforms.org/Profile/oauth-uris#register")
	UriType registerUri = new UriType()
	registerUri.setValue(oauth.registerUri)
	registerUriExtension.setValue(registerUri)
	
	Extension authorizeUriExtension = new Extension()
	authorizeUriExtension.setUrlSimple("http://fhir-registry.smartplatforms.org/Profile/oauth-uris#authorize")
	UriType authorizeUri = new UriType()
	authorizeUri.setValue(oauth.authorizeUri)
	authorizeUriExtension.setValue(authorizeUri)
	
	Extension tokenUriExtension = new Extension()
	tokenUriExtension.setUrlSimple("http://fhir-registry.smartplatforms.org/Profile/oauth-uris#token")
	UriType tokenUri = new UriType();
	tokenUri.setValue(oauth.tokenUri)
	tokenUriExtension.setValue(tokenUri)
	
	CodeableConcept newService = new CodeableConcept()
	Coding newCoding = new Coding()
	newCoding.setSystemSimple("http://hl7.org/fhir/vs/restful-security-service")
	newCoding.setCodeSimple("OAuth2")
	newService.getCoding().add(newCoding)
	newService.setTextSimple("OAuth version 2 (see oauth.net).")
	
	ConformanceRestSecurityComponent newSecurity = new ConformanceRestSecurityComponent()
	newSecurity.setDescriptionSimple("SMART on FHIR uses OAuth2 for authorization")
	newSecurity.getService().add(newService)
    
    if (oauth.enabled) {
        List<Extension> extensions = newSecurity.getExtensions()
        extensions.add(registerUriExtension)
        extensions.add(authorizeUriExtension)
        extensions.add(tokenUriExtension)
    }
    
	conformance.getRest().get(0).setSecurity(newSecurity)
	
    List supportedOps = [
      TypeRestfulOperation.read,
      TypeRestfulOperation.vread,
      TypeRestfulOperation.update,
      TypeRestfulOperation.searchtype,
      TypeRestfulOperation.create,
      TypeRestfulOperation.historytype,
      TypeRestfulOperation.historyinstance,
      SystemRestfulOperation.transaction,
      SystemRestfulOperation.historysystem
    ]
	
    conformance.rest.each { ConformanceRestComponent r  ->
      r.operation = r.operation.findAll { ConformanceRestOperationComponent o ->
        o.codeSimple in supportedOps
      }
      r.resource.each { ConformanceRestResourceComponent rc ->

        String resourceName = rc.typeSimple
        String profile = "resources/${resourceName}.profile.xml".toLowerCase()

        Profile p = resourceFromFile(profile) 
        def paramDefs = p.structure.collect{it.searchParam}.flatten()

        def typesForParam = p.structure.collect({it.element}).flatten().collectEntries {
          [(it.pathSimple) : it.definition.type.findAll({it.profileSimple }).collect{
            it.profileSimple.split("/")[-1]
          }]
        }
 
        rc.searchParam.each { searchParam ->
          String paramName = searchParam.nameSimple
          String key = resourceName + '.' + paramName
          String xpath = paramDefs.find{it.nameSimple == paramName}.xpathSimple
	  if (xpath) {
            def types = typesForParam[xpathToFhirPath(xpath)]
            if (types) {
              xpathReferenceTypes.put(key, types);
            }
          }

          // If we don't have a spot fix already loaded for this searchParam
          // then use the xapth we discovered in the default Profile (if any)
          if (xpath && !spotFixes[key]) xpathFixes.put(key, xpath)
        }

        rc.operation = rc.operation.findAll { ConformanceRestResourceOperationComponent o ->
          o.codeSimple in supportedOps
        }
      }
    }

    searchParamXpaths = xpathFixes.build()
    searchParamReferenceTypes = xpathReferenceTypes.build()
  }
}
