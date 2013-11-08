
package fhir;

import java.text.SimpleDateFormat

import javax.annotation.PostConstruct
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory
import java.util.regex.Pattern

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
		restResources.each { resource ->
			Class model = classForModel resource.typeSimple

			indexersByResource[model] = resource.searchParam.collect {	searchParam ->

				String key = searchParam.sourceSimple

				// Short-circuit FHIR's built-in xpath if defined. Handles:
				//  * missing xpaths
				//  * broken xpaths  -- like 'f:value[x]'
				SearchParamHandler.create(
						searchParam.nameSimple,
						searchParam.typeSimple,
						xpathsMissingFromFhir[key] ?:searchParam.xpathSimple);
			}
		}

		idIndexer = new IdSearchParamHandler(searchParamName: "_id", fieldType: SearchParamType.token, xpath: null);
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
			[(it.searchParamName): it]
		}
	}

	List paramAsList(v) {
		// grails turns subject._id into two params: "subject._id" and "subject" which is a map w/ id
		if  (v == null) return []
		if (v.class == null) return null
		if (v instanceof String[])
			return v as List
		return [v]
	}

	private List<String> splitOne(String str, String c){
		if  (str.indexOf(c) != -1) {
			List<String> parts = str.split(Pattern.quote(c))
			if (parts.size == 1)
				return [parts[0], ""]
			return [parts[0], parts[1..-1].join(c)]
		}
		return null
	}

	private List chainParts (String searchParam) {
		def parts = splitOne(searchParam, '.')
		if (parts == null) return [false, searchParam]
		return [true] + parts
	}

	private List paramParts (String searchParam) {
		def parts  = splitOne(searchParam, ':')
		if (parts == null) return [searchParam]
		return parts
	}

	public List<SearchedValue> queryToHandlerTree (Map params) {
		List<SearchedValue> ret = []
		Map indexerFor = indexerFor(params.resource)

		// Represent each term in the query a
		// key, modifier, and value
		// e.g. [key: "date", modifier: "after", value: "2010"]
		params.each { String searchParam, searchValues ->

			searchValues = paramAsList(searchValues)
			if (!searchValues) { return }

			def (isChained, beforeChain, afterChain) = chainParts(searchParam)
			def (paramName, modifier) = paramParts(beforeChain)

			SearchParamHandler indexer = indexerFor[paramName]
			if (!indexer) return
				searchValues.each { String searchValue ->
					if (isChained) { // a chaining query -- need to recurse
						def resourceName = modifier
						ret += new SearchedValue( handler: indexer, modifier: resourceName)
						ret += [queryToHandlerTree([ resource: resourceName, (afterChain): searchValue ])]
					} else {
						ret += new SearchedValue( handler: indexer, modifier: modifier, values: searchValue)
					}
				}
		}

		log.error("returning Params: $ret")
		return ret
	}

	private boolean isFirstClause(List current) {
		return current.size() == 1 && current[0] == 1
	}

	def joinClauses(List clauseTree) {
		joinClauses(clauseTree, [0], false)
	}

	def joinClauses(List clauseTree, List<Integer> relativeTo, boolean push) {

		if (clauseTree.size() == 0) return [query:[],params:[:]]

		def headClause = clauseTree[0]
		def remainingClauses = clauseTree.subList(1, clauseTree.size())

		if (headClause instanceof List) {
			return joinClauses(headClause, relativeTo, true)
		}

		List<Integer> current = relativeTo.collect{it}
		if (push) current += 0 else current[-1]++

		String prevRel = relativeTo.join("_")
		String rel = current.join("_")
		List<String> query = []
		Map params = [:]

		def (table, List fields) = headClause.handler.joinOn(headClause)
		def fieldStrings = fields.collect { f ->
			'  table_'+rel+'.'+f.name+' '+(f.operation ?: '=')+' :value_'+rel+'_'+f.name
		}
		params += fields.collectEntries { f -> [('value_'+rel+'_'+f.name): f.value] }
		params += [('field_'+rel): headClause.handler.searchParamName]

		query += 'JOIN resource_index_term table_'+rel+' ON (\n' +
				'  table_'+rel+'.search_param = :field_'+rel+ ' AND \n' +
				'  table_'+prevRel+'.'+(push ? 'reference':'fhir')+'_type = table_'+rel+'.fhir_type AND \n' +
				'  table_'+prevRel+'.'+(push ? 'reference':'fhir')+'_id = table_'+rel+'.fhir_id AND \n' +
				fieldStrings.join(' AND \n') +
				'  )'

		def remaining = joinClauses(remainingClauses, current, false)
		query += remaining.query
		params += remaining.params
		return [query: query, params: params]
	}

	Map  sorts = [
		asc: [
			fn: "min",
			dir: "asc"
		], desc: [
			fn: "max",
			dir: "desc"
		]
	]

	private Map sortDirection(String sort) {
		if (sort.indexOf(":") == -1) sorts.asc
		return sorts[sort.split(":")[1]]
	}

	private sortClauses(params, cs){
		Map indexerFor = indexerFor(params.resource)
		cs.query = ["select max(table_0.version_id) as version_id, \n"+
			"row_number() OVER \n"+
			"(ORDER BY @fullOrderClause@) AS sortfield \n"+
			"FROM resource_index_term table_0"] + cs.query

		cs.params += [
			type: params.resource
		]

		String typeClause = "WHERE table_0.fhir_type=:type "
		List<Map> orderBy =  []

		def _sort = [
			asc: paramAsList(params._sort) + paramAsList(params.'_sort:asc'),
			desc: paramAsList(params['_sort:desc'])
		]

		def applySort = { Map sortDirection, String sortParam ->

			def sortParamNum = orderBy.size()
			def orderTable = "order_by_"+sortParamNum
			SearchParamHandler indexer = indexerFor[sortParam]
			orderBy +=  [
				sortDirection: sortDirection,
				column: orderTable+"."+indexer.orderByColumn
			]

			cs.params += [
				("sort_param_"+sortParamNum): indexer.searchParamName
			]

			cs.query += """JOIN resource_index_term ${orderTable} on (
                    table_0.fhir_type=${orderTable}.fhir_type AND
                    table_0.fhir_id=${orderTable}.fhir_id AND
                    ${orderTable}.search_param = :sort_param_${sortParamNum}
                    )"""
		}

		_sort.asc.each {it -> applySort(sorts.asc, it)}
		_sort.desc.each {it -> applySort(sorts.desc, it)}
		orderBy += [sortDirection: sorts.asc, column: "table_0.version_id"]

		def fullOrderClause = orderBy.collect{Map o ->
			"${o.sortDirection.fn}("+o.column+") ${o.sortDirection.dir}"
		}.join(", ")

		cs.query += typeClause +
				"GROUP BY table_0.fhir_type, table_0.fhir_id \n" +
				"ORDER BY $fullOrderClause"

		cs.fullOrderClause = fullOrderClause
		return cs
	}

	public BasicDBObject searchParamsToSql(Map params){

		List<SearchedValue> clauseTree = queryToHandlerTree(params)
		def cs = joinClauses(clauseTree)
		def sorted = sortClauses(params, cs)

		String q = sorted.query.join("\n")
		q = sorted.query.join("\n")
		q = q.replace("@fullOrderClause@", sorted.fullOrderClause)
		q = "select v.version_id, v.fhir_type, v.fhir_id, v.content from resource_version v join (\n"+
				q + "\n) ol \n" +
				"on ol.version_id=v.version_id order by ol.sortfield"

		String printQ = q
		sorted.params.each {k,v->
			println("Replacing $k $v")
			printQ = printQ.replaceAll(Pattern.compile(':'+k+'\\s'), "'$v' ")
		}
		println("replaced all")
		println(printQ)

		sorted.query = [q]

		return sorted
	}
}
