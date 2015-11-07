package fhir
import java.util.Map;
import java.util.regex.Matcher

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.instance.model.CodeableConcept
import org.hl7.fhir.instance.model.Coding
import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.DateTimeType
import org.hl7.fhir.instance.model.Extension
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.SearchParameter;
import org.hl7.fhir.instance.model.UriType
import org.hl7.fhir.instance.model.Conformance.ConformanceRestComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestOperationComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestSecurityComponent
import org.hl7.fhir.instance.model.Conformance.ResourceInteractionComponent;
import org.hl7.fhir.instance.model.Conformance.SystemInteractionComponent;
import org.hl7.fhir.instance.model.Conformance.SystemRestfulInteraction
import org.hl7.fhir.instance.model.Conformance.TypeRestfulInteraction
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
  static XmlParser fhirXml = new XmlParser()
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
    fhirXml.parse(stream)
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

    conformance = resourceFromFile "resources/conformance-base.xml"

    conformance.text.div = new XhtmlNode(NodeType.Element, "div");
    conformance.text.div.addText("Generated Conformance Statement -- see structured representation.")
    conformance.url = urlService.fhirBase + '/conformance'
    conformance.publisher = "SMART on FHIR"
    conformance.name =  "SMART on FHIR Conformance Statement"
    conformance.description = "Describes capabilities of this SMART on FHIR server"

    conformance.setDate(new Date())

    if (oauth.enabled) {
	Extension smartAuthExtension = new Extension()
	smartAuthExtension.setUrl( "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris")
    
	Extension registerUriExtension = new Extension()
	registerUriExtension.setUrl( "register")
	UriType registerUri = new UriType()
	registerUri.setValue(oauth.registerUri)
	registerUriExtension.setValue(registerUri)
	
	Extension authorizeUriExtension = new Extension()
	authorizeUriExtension.setUrl("authorize")
	UriType authorizeUri = new UriType()
	authorizeUri.setValue(oauth.authorizeUri)
	authorizeUriExtension.setValue(authorizeUri)
	
	Extension tokenUriExtension = new Extension()
	tokenUriExtension.setUrl("token")
	UriType tokenUri = new UriType();
	tokenUri.setValue(oauth.tokenUri)
	tokenUriExtension.setValue(tokenUri)
	
	CodeableConcept newService = new CodeableConcept()
	Coding newCoding = new Coding()
	newCoding.setSystem("http://hl7.org/fhir/restful-security-service")
	newCoding.setCode("SMART-on-FHIR")
	newService.getCoding().add(newCoding)
	newService.setText("OAuth2 using SMART-on-FHIR profile (see http://docs.smarthealthit.org)")
	
	ConformanceRestSecurityComponent newSecurity = new ConformanceRestSecurityComponent()
	newSecurity.setDescription("SMART on FHIR uses OAuth2 for authorization")
	newSecurity.getService().add(newService)
    
	smartAuthExtension.getExtension().add(registerUriExtension)
	smartAuthExtension.getExtension().add(authorizeUriExtension)
	smartAuthExtension.getExtension().add(tokenUriExtension)
	newSecurity.getExtension().add(smartAuthExtension)
    
        conformance.getRest().get(0).setSecurity(newSecurity)
    }
	
    List supportedOps = [
      TypeRestfulInteraction.READ,
      TypeRestfulInteraction.VREAD,
      TypeRestfulInteraction.UPDATE,
      TypeRestfulInteraction.SEARCHTYPE,
      TypeRestfulInteraction.CREATE,
      TypeRestfulInteraction.HISTORYTYPE,
      TypeRestfulInteraction.HISTORYINSTANCE,
      SystemRestfulInteraction.TRANSACTION,
      SystemRestfulInteraction.HISTORYSYSTEM
    ]
    
    
    // TODO get search param names lined up so we can index correctly
    // 1. From conformance look at each search param URL
    // 2. Transform to lower-case, strip "#", add dashes
    
    // If we don't have a spot fix already loaded for this searchParam
    // then use the xpath we discovered in the default bundle of SearchParameteres

    Bundle allSearchParams = resourceFromFile("resources/search-parameters.xml")
    allSearchParams.entry.each { BundleEntryComponent be ->
      SearchParameter sp = be.resource
      String key = sp.base + ':' + sp.name
      String xpath = sp.xpath
      println "eval param ${key} ${xpath}"
      
      if (xpath) {
        def types = sp.target.collect {t -> t.value}
        //println "Types for ${key}: ${types}"
        if (types) {
          xpathReferenceTypes.put(key, types);
        } else {
          println "but not ref types"

         }
      }
      if (xpath && !spotFixes[key]) xpathFixes.put(key, xpath)
    }

	
    conformance.rest.each { ConformanceRestComponent r ->
    
      r.interaction = r.interaction.findAll { SystemInteractionComponent o ->
        o.hasCode() && o.codeElement in supportedOps
      }
      r.resource.each { ConformanceRestResourceComponent rc ->
        
        rc.interaction = rc.interaction.findAll { ResourceInteractionComponent o ->
          o.code in supportedOps
        }
      }
    }

    searchParamXpaths = xpathFixes.build()
    println "Got som xpaths with ${searchParamXpaths['Condition:patient']}"
    searchParamReferenceTypes = xpathReferenceTypes.build()
  }
}
