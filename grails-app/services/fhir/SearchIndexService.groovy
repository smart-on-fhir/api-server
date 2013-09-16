
package fhir;

import java.text.SimpleDateFormat

import javax.annotation.PostConstruct
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.Conformance.RestfulOperation;
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.ConformanceRestComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceOperationComponent
import org.hl7.fhir.utilities.xhtml.NodeType
import org.hl7.fhir.utilities.xhtml.XhtmlNode
import org.springframework.util.xml.SimpleNamespaceContext
import org.xml.sax.InputSource

import com.google.common.collect.ImmutableMap
import com.mongodb.BasicDBObject

import fhir.searchParam.SearchParamHandler
import fhir.searchParam.SearchParamValue

class SearchIndexService{

	static def transactional = false
	static def lazyInit = false

	static GrailsApplication grailsApplication
	static XPath xpathEvaluator = XPathFactory.newInstance().newXPath();
	static SimpleNamespaceContext nsContext
	static BundleService bundleService
	static Conformance conformance
	static XmlParser parser = new XmlParser()	

	static Map<Class<Resource>,Collection> indexersByResource = [:]
	static Map<String, String> xpathsMissingFromFhir;
	static Map<String, String> capitalizedModelName = [:]

	@PostConstruct
	void init() {
		configureXpathSettings();

		Conformance conformance = resourceFromFile "profile.xml"

		conformance.text.div = new XhtmlNode();
	    conformance.text.div.nodeType = NodeType.Element
	    conformance.text.div.name = "div"
		conformance.text.div.addText("Generated Conformance Statement -- see structured representation.")
		conformance.identifierSimple = bundleService.fhirBase + '/conformance'
		conformance.publisherSimple = "SMART on FHIR"
		conformance.nameSimple =  "SMART on FHIR Conformance Statement"
		conformance.descriptionSimple = "Describes capabilities of this SMART on FHIR server"
		conformance.telecom[0].valueSimple = bundleService.fhirBase

	    def format = new SimpleDateFormat("yyyy-MM-dd")
		conformance.dateSimple = format.format(new Date())
		
		List supportedOps = [
			RestfulOperation.read,
			RestfulOperation.vread,
			RestfulOperation.update,
			RestfulOperation.search,
			RestfulOperation.create,
			RestfulOperation.transaction,
			RestfulOperation.historysystem,
			RestfulOperation.historytype,
			RestfulOperation.historyinstance
		]
		
		conformance.rest.each { ConformanceRestComponent r  ->
			r.resource.each { ConformanceRestResourceComponent rc ->
				rc.operation = rc.operation.findAll { ConformanceRestResourceOperationComponent o ->
					o.codeSimple in supportedOps
				}
			}
		}

		setConformance(conformance)
	}

	private configureXpathSettings() {
		nsContext = new SimpleNamespaceContext();
		grailsApplication.config.fhir.namespaces.each {
			prefix, uri -> nsContext.bindNamespaceUri(prefix, uri)
		}
		xpathEvaluator.setNamespaceContext(nsContext)
		SearchParamHandler.injectXpathEvaluator(xpathEvaluator)

		def xpathFixes = ImmutableMap.<String, String> builder();
		grailsApplication.config.fhir.searchParam.spotFixes.each {
			uri, xpath -> xpathFixes.put(uri, xpath)
		}
		xpathsMissingFromFhir = xpathFixes.build()
	}

	public Class<Resource> classForModel(String modelName){
		modelName = capitalizedModelName[modelName]?:modelName
		if(modelName.equals("String")){
			modelName += "_";
		}
		if(modelName.equals("List")){
			modelName += "_";
		}
		return lookupClass("org.hl7.fhir.instance.model."+modelName);
	}


	private static Resource resourceFromFile(String file) {
		def stream = classLoader.getResourceAsStream(file)
		parser.parse(stream)
	}

	public static Class lookupClass(String name){
		Class.forName(name,	true, classLoader)
	}

	public static ClassLoader getClassLoader(){
		Thread.currentThread().contextClassLoader
	}


	public void setConformance(Conformance c) throws Exception {
		log.debug("Setting conformance profile")
		conformance = c
		def restResources = c.rest[0].resource
		capitalizedModelName["binary"] = "Binary"
		restResources.each { resource ->
			capitalizedModelName[resource.typeSimple.toLowerCase()] = resource.typeSimple
			Class model = classForModel resource.typeSimple

			indexersByResource[model] = resource.searchParam.collect {	searchParam ->

				String key = searchParam.sourceSimple

				// Short-circuit FHIR's built-in xpath if defined. Handles:
				//  * missing xpaths -- like in Patient
				//  * broken xpaths  -- like 'f:value[x]'
				SearchParamHandler.create(
						searchParam.nameSimple,
						searchParam.typeSimple,
						xpathsMissingFromFhir[key] ?:searchParam.xpathSimple);
			}
		}
	}

	public static org.w3c.dom.Document fromResource(Resource r) throws IOException, Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		org.w3c.dom.Document d = builder.parse(new InputSource(new StringReader(r.encodeAsFhirXml())));
		return d;
	}

	public List<SearchParamValue> indexResource(Resource rx) {

		log.info("\n\nExtracting search index terms for a new " + rx.class)

		Collection indexers = indexersByResource[rx.class]
		if (!indexers){
			return []
		}

		org.w3c.dom.Document rdoc = fromResource(rx)
		def ret = indexers.collectMany {
			SearchParamHandler h -> h.execute(rdoc)
		} findAll { SearchParamValue v ->
			v.paramValue != ""
		}

		log.info("# index fields: " + ret.size())
		return ret;
	}


	public BasicDBObject queryParamsToMongo(Map params){
		log.debug("generating clauses for $params")
		def rc = classForModel(params.resource)
		def indexers = indexersByResource[rc] ?: []

		// Just the indexers for the current resource type
		// keyed on the searchParam name (e.g. "date", "subject")
		Map<String,SearchParamHandler> indexerFor = indexers.collectEntries {
			[(it.fieldName): it]
		}

		// Represent each term in the query a
		// key, modifier, and value
		// e.g. [key: "date", modifier: "after", value: "2010"]
		def searchParams = params.collectMany { k,v ->
			def c = k.split(":") as List
			if (v instanceof String[]) v = v as List
			else v = [v]
			return v.collect {oneVal ->
				[key: c[0], modifier: c[1], value: oneVal]
			}
		}.findAll {
			it.key in indexerFor
		}

		// Run the assigned indexer on each term
		// to generate an AND'able list of MongoDB
		// query clauses.
		List<BasicDBObject> clauses = searchParams.collect {
			def idx = indexerFor[it.key]
			List orClauses = idx.orClausesFor(it).collect {
				idx.searchClause(it)
			}

			orClauses.size() == 1 ? orClauses[0] :
					SearchParamHandler.orList(orClauses)
		}
		clauses = clauses + [type:capitalizedModelName[params.resource]]
		return SearchParamHandler.andList(clauses)
	}
}