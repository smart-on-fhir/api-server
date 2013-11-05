
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
import org.hl7.fhir.instance.model.Conformance.ConformanceRestOperationComponent
import org.hl7.fhir.instance.model.Conformance.SystemRestfulOperation;
import org.hl7.fhir.instance.model.Conformance.TypeRestfulOperation;
import org.hl7.fhir.instance.model.Conformance.SearchParamType;
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
import com.mongodb.DBObject

import fhir.searchParam.IdSearchParamHandler
import fhir.searchParam.IndexedValue
import fhir.searchParam.SearchParamHandler
import fhir.searchParam.SearchedValue

class SearchIndexService{

	static def transactional = false
	static def lazyInit = false

	static GrailsApplication grailsApplication
	static XPath xpathEvaluator = XPathFactory.newInstance().newXPath();
	static SimpleNamespaceContext nsContext
	static UrlService urlService
	static Conformance conformance
	static XmlParser parser = new XmlParser()

	static Map<Class<Resource>,Collection> indexersByResource = [:]
	static Map<String, String> xpathsMissingFromFhir;
	static Map<String, String> capitalizedModelName = [:]
	static IdSearchParamHandler idIndexer;

	@PostConstruct
	void init() {
		configureXpathSettings();

		Conformance conformance = resourceFromFile "profile.xml"

		conformance.text.div = new XhtmlNode();
		conformance.text.div.nodeType = NodeType.Element
		conformance.text.div.name = "div"
		conformance.text.div.addText("Generated Conformance Statement -- see structured representation.")
		conformance.identifierSimple = urlService.fhirBase + '/conformance'
		conformance.publisherSimple = "SMART on FHIR"
		conformance.nameSimple =  "SMART on FHIR Conformance Statement"
		conformance.descriptionSimple = "Describes capabilities of this SMART on FHIR server"
		conformance.telecom[0].valueSimple = urlService.fhirBase

		def format = new SimpleDateFormat("yyyy-MM-dd")
		conformance.dateSimple = format.format(new Date())

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
		SearchParamHandler.injectUrlService(urlService)

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

		idIndexer = new IdSearchParamHandler(fieldName: "_id", fieldType: SearchParamType.token, xpath: null);
	}

	public static org.w3c.dom.Document fromResource(Resource r) throws IOException, Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		org.w3c.dom.Document d = builder.parse(new InputSource(new StringReader(r.encodeAsFhirXml())));
		return d;
	}

	public List<IndexedValue> indexResource(Resource rx) {

		log.info("\n\nExtracting search index terms for a new " + rx.class)

		Collection indexers = indexersByResource[rx.class]
		if (!indexers){
			return []
		}

		org.w3c.dom.Document rdoc = fromResource(rx)
		def ret = indexers.collectMany {
			SearchParamHandler h -> h.execute(rdoc)
		}

		log.info("Logged")
		log.info(ret.each { IndexedValue p ->
			p.dbFields.each {k,v ->
				println("$p.paramName ($p.handler.fieldType): $k -> $v\t\t")
			}
		})

		log.info("# index fields: " + ret.size())
		return ret;
	}

	public void queryParamsToSql(Map params){

	}
	
	
	Map<String,SearchParamHandler> indexerFor(String resourceName){
		def rc = classForModel(resourceName)
		def indexers = indexersByResource[rc] ?: []
		indexers = indexers + idIndexer;

		// Just the indexers for the current resource type
		// keyed on the searchParam name (e.g. "date", "subject")
		return indexers.collectEntries { SearchParamHandler it ->
			[(it.fieldName): it]
		}
	}
	
	List paramAsList(v) {
		// grails turns subject._id into two params: "subject._id" and "subject" which is a map w/ id
		if (v.class == null) return null
		if (v instanceof String[])
			return v as List
		return [v]
	}

	public List<SearchedValue> queryToHandlerTree (Map params) {
		List<SearchedValue> ret = []
		Map indexerFor = indexerFor(params.resource)

		// Represent each term in the query a
		// key, modifier, and value
		// e.g. [key: "date", modifier: "after", value: "2010"]
		params.each { String k, v ->

			v = paramAsList(v)
			if (!v) return

			List paramParts = k.split(":")
			String paramName = paramParts[0]
			String modifier = paramParts.size() > 1 ? paramParts[1..-1].join(":") : ""

			SearchParamHandler indexer = indexerFor[paramName]
			if (!indexer) return

			v.each {String oneVal ->
				if (indexer.fieldType == SearchParamType.reference && modifier.indexOf('.') != -1) {
					// a chaining query -- need to recurse

					def modifierParts = modifier.split("\\.")
					def resource = modifierParts[0]
					def nextModifier = modifierParts.size() > 1 ? modifierParts[1..-1].join(".") : null

					ret += new SearchedValue( handler: indexer, modifier: resource, values: "*")

					ret += [queryToHandlerTree([
							  resource: resource, (nextModifier): oneVal
							])]
				} else {
					ret += new SearchedValue(
							handler: indexer,
							modifier: modifier,
							values: oneVal)
				}
			}
		}

		log.error("returning Params: $ret")
		return ret
	}

	void sqlClauses(List searchedFor, List<Integer> relativeTo, boolean push) {

		if (searchedFor.size() == 0) return

		List<Integer> prev = relativeTo.collect{it}
		List<Integer> current = relativeTo.collect{it}

		def head = searchedFor[0]
		def rest = searchedFor.subList(1, searchedFor.size())

		if (head instanceof List) {
			sqlClauses(head, relativeTo, true)
		} else {
			if (push) current += 0 else current[-1]++
			String prevRel = prev.join("_")
			String rel = current.join("_")
			if (push)
			log.debug("JOIN resource_${head.handler.fieldType}_index table_$rel: on table_${prevRel}.reference_type=table_${rel}.type and table_${rel}.vals '$head.values'")
			else 
			log.debug("JOIN resource_${head.handler.fieldType}_index table_$rel: on table_${prevRel}.type=table_${rel}.type and table_${rel}.vals '$head.values'")
		}

		sqlClauses(rest, current, false)

	}

	List buildClauseTree(List<SearchedValue> vs, List ret){
		vs.each { SearchedValue v->
			if (v.handler.fieldType == SearchParamType.reference) {
				if (v.values.indexOf('.') == -1) {
					// chained search on a resource reference
					// recurse to generate chainSteps
					// so the final clause tree is a tree where all leaves are SearchedValues
					// which can directly generate their simple matchers
					ret.add(parseQueryParams([

					]))

				}
			}
			ret += v.handler.clausesFor(v)
		}
	}

	public BasicDBObject queryParamsToMongo(Map params){
		// Run the assigned indexer on each term
		// to generate an AND'able list of MongoDB
		// query clauses.
		List<SearchedValue> clauseTree = queryToHandlerTree(params)
		println("Compete handler tree: $clauseTree")
		sqlClauses(clauseTree, [0], false)
		return

		//List clauseTree = buildClauseTree(searchedFor, [])
		//clauseTree += [[searchedFor[1], searchedFor[1]]]
		//clauseTree += [searchedFor[1], searchedFor[1]]

		List<BasicDBObject> clauses = clauseTree.collect { SearchedValue v ->
			def idx = v.handler

			List orClauses = idx.orClausesFor(v).collect {
				idx.searchClause(v)
			}

			return orClauses.size() == 1 ?
			orClauses[0] :
			SearchParamHandler.orList(orClauses)
		}
		clauses = clauses + [fhirType:capitalizedModelName[params.resource]]
		return SearchParamHandler.andList(clauses)
	}

}
