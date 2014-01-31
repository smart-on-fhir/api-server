package fhir;

import javax.annotation.PostConstruct
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants

import java.util.regex.Pattern

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.Conformance.ConformanceRestOperationComponent
import org.hl7.fhir.instance.model.Conformance.SystemRestfulOperation;
import org.hl7.fhir.instance.model.Conformance.TypeRestfulOperation;
import org.hl7.fhir.instance.model.Conformance.SearchParamType;
import org.hl7.fhir.instance.model.DateAndTime
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

import fhir.AuthorizationService.Authorization
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

    conformance.text.div = new XhtmlNode(NodeType.Element, "div");
    conformance.text.div.addText("Generated Conformance Statement -- see structured representation.")
    conformance.identifierSimple = urlService.fhirBase + '/conformance'
    conformance.publisherSimple = "SMART on FHIR"
    conformance.nameSimple =  "SMART on FHIR Conformance Statement"
    conformance.descriptionSimple = "Describes capabilities of this SMART on FHIR server"
    conformance.telecom[0].valueSimple = urlService.fhirBase

    conformance.dateSimple = DateAndTime.now()

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
    grailsApplication.config.fhir.namespaces.each { prefix, uri ->
      println("Confugring xpath $prefix -> $uri")
      nsContext.bindNamespaceUri(prefix, uri)
    }
    xpathEvaluator.setNamespaceContext(nsContext)

    SearchParamHandler.injectXpathEvaluator(xpathEvaluator)
    SearchParamHandler.injectUrlService(urlService)

    def xpathFixes = ImmutableMap.<String, String> builder();
    grailsApplication.config.fhir.searchParam.spotFixes.each { uri, xpath ->
      xpathFixes.put(uri, xpath)
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
            resource.typeSimple,
            xpathsMissingFromFhir[key] ?:searchParam.xpathSimple);
      } + new IdSearchParamHandler(searchParamName: "_id", fieldType: SearchParamType.token, xpath: null, resourceName: resource.typeSimple);
    }
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
    def ret = indexers
        .findAll{ ! (it instanceof IdSearchParamHandler )}
        .collectMany { SearchParamHandler h -> h.execute(rdoc) }

    log.info("Logged")
    log.info(ret.each { IndexedValue p ->
      p.dbFields.each {k,v ->
        println("$p.paramName ($p.handler.fieldType): $k -> $v\t\t")
      }
    })

    log.info("# index fields: " + ret.size())
    return ret;
  }


  Map<String,SearchParamHandler> indexerFor(String resourceName){
    def rc = classForModel(resourceName)
    def indexers = indexersByResource[rc] ?: []

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
      if (parts.size() == 1)
        return [parts[0], ""]
      return [
        parts[0],
        parts[1..-1].join(c)
      ]
    }
    return null
  }

  private List chainParts (String searchParam) {
    def parts = splitOne(searchParam, '.')
    if (parts == null) return [false, searchParam]
    return [true]+ parts
  }

  private List paramParts (String searchParam) {
    def parts  = splitOne(searchParam, ':')
    if (parts == null) return [searchParam]
    return parts
  }


  /*	Take a raw Grails query params map, and returns a tree of SearchedValues.
   Each SearchedValue has a SearchParamHandler, modifier, and value. So for example, a
   query that looked like /Patient?identifier=123,456 would return a list with a single
   SearchedValue like:
   handler=(the TokenSearchParamHandler for Patient.identifier), 
   modifier=null
   values="123,456"
   The fun begins with chained queries. In this case, the results includes
   a sub-list for each chained parameter. The sub-list is preceded by an unbound
   match for the reference search param. For example a query like
   /Condition?subject:Patient.identifer=123 would return a tree like:
   0. SearchedValue
   handler=(ReferenceSearchParamHandler for Condition.subject)
   modifier=null,
   values=null
   1. [
   0. SearchedValue
   handler=(TokenSearchParamHandler for for Patient.identifier)
   modifier=null,
   values="123"
   ]
   */
  public List<SearchedValue> queryToHandlerTree (Map params) {
    List<SearchedValue> ret = []
    Map indexerFor = indexerFor(params.resource)

    // Represent each term in the query a
    // key, modifier, and value
    // e.g. [key: "date", modifier: "after", value: "2010"]
    params.each { String searchParam, searchValues ->

      searchValues = paramAsList(searchValues)
      if (!searchValues || searchValues[0]=="") { return }

      def (isChained, beforeChain, afterChain) = chainParts(searchParam)
      def (paramName, modifier) = paramParts(beforeChain)

      SearchParamHandler indexer = indexerFor[paramName]
      if (!indexer) return
        searchValues.each { String searchValue ->
          if (isChained) { // a chaining query -- need to recurse
            def resourceName = modifier
            ret += new SearchedValue( handler: indexer, modifier: resourceName)
            ret += [
              queryToHandlerTree([ resource: resourceName, (afterChain): searchValue ])
            ]
          } else {
            ret += new SearchedValue( handler: indexer, modifier: modifier, values: searchValue)
          }
        }
    }

    log.error("returning Params: $ret")
    return ret
  }

  private String fieldSnippet(int clauseNum, int phraseNum, Map field) {
    String lowerCaseOp = field.operation ? field.operation.toString().toLowerCase() : null

    if (lowerCaseOp == 'is null') {
      return "${field.name} is null"
    }
    if (lowerCaseOp == 'is not null') {
      return "${field.name} is not null"
    }

    return field.name+" "+(field.operation ?: '=')+' :value_'+clauseNum+'_'+phraseNum+'_'+field.name
  }

  int clauseNum=0
  def joinClauses(List clauseTree, String resourceName, Authorization a) {

    List<String> query = []
    Map params = [:]

    clauseTree.each { clause ->
      clauseNum++
      Map remaining

      if (clause instanceof List) {
        remaining =  joinClauses(clause, null, a)
        query[-1] += " AND (reference_type, reference_id) in (\n" + remaining.query.join("\nINTERSECT\n") + "\n)"
        params += remaining.params
      } else {

        def orFields = clause.handler.joinOn(clause)
        List orClauses =[]

        params += [('field_'+clauseNum): clause.handler.searchParamName]
        params += [('type_'+clauseNum): clause.handler.resourceName]

        orFields.each { orf ->
          int phraseNum=orClauses.size()

          def fieldStrings = orf.collect { fieldSnippet(clauseNum,phraseNum, it) }
          orClauses.add(fieldStrings.join(" AND "))

          params += orf.collectEntries { f ->
            [('value_'+clauseNum+'_'+phraseNum+'_'+f.name): f.value]
          }

        }

        query +=  " SELECT fhir_type, fhir_id from resource_index_term where " +
            "fhir_type = :type_${clauseNum} " +
            (clause.handler instanceof IdSearchParamHandler ? "" : """
					AND search_param = :field_${clauseNum} 
					""") +
            (orClauses.size() == 0 ? "" :  (" AND " + """( ${orClauses.join(" OR \n") } )"""))
      }
    }

    if (query.size() == 0) {
      clauseNum++;
      query  += " select fhir_type, fhir_id from resource_index_term where fhir_type = :type_${clauseNum} "
      params += [('type_'+clauseNum): resourceName]
    }

    if (a.accessIsRestricted && resourceName != null) {
      //TODO remove resourceName != null restriction to enforce compartment permissions on joined resources
      //TODO interpolate arrays into queries to prevent SQL injection
      query  += "select fhir_type, fhir_id from resource_compartment where compartments  &&  ${a.compartmentsSql}"
    }

    return [query: [query.join("\nINTERSECT\n")], params: params]

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

  private fullOrderClause(List<Map> orderBy) {
    (orderBy.collect{Map o -> """
			(select ${o.sortDirection.fn}(${o.column}) from resource_index_term t where 
				t.search_param=:${o.searchParamName} and t.fhir_type=s.fhir_type and t.fhir_id=s.fhir_id) ${o.sortDirection.dir}
			""" } + """
			s.version_id asc
		""").join(", ")
  }

  private sortClauses(params, cs){

    Map indexerFor = indexerFor(params.resource)

    List<Map> orderBy =  []

    def _sort = [
      asc: paramAsList(params._sort) + paramAsList(params.'_sort:asc'),
      desc: paramAsList(params['_sort:desc'])
    ]

    def applySort = { Map sortDirection, String sortParam ->
      String sp = "sort_param_"+orderBy.size()
      orderBy +=  [
        sortDirection: sortDirection,
        column: indexerFor[sortParam].orderByColumn,
        searchParamName:  sp
      ]
      cs.params += [
        (sp): indexerFor[sortParam].searchParamName
      ]
    }

    _sort.asc.each {it -> applySort(sorts.asc, it)}
    _sort.desc.each {it -> applySort(sorts.desc, it)}

    return [

      query: [
        """
                        select v.content, v.fhir_id, v.fhir_Type, v.version_id from resource_Version v join ( SELECT s.version_id,
                        row_number() over (ORDER BY ${fullOrderClause(orderBy)} ) as sortv
                                                
                        from resource_index_term s 
                        where
                        (s.fhir_type, s.fhir_id) in 
                        ( ${cs.query[0]} )
                        group by s.version_id, s.fhir_type, s.fhir_id
                        ORDER BY  ${fullOrderClause(orderBy)} 
                        ) o on v.version_id=o.version_id order by sortv
			"""
      ],
      params: cs.params
    ]
  }

  public Map<String,String> searchParamsToSql(Map params, Authorization a, paging){

    List<SearchedValue> clauseTree = queryToHandlerTree(params)
    def clauses = joinClauses(clauseTree, params.resource, a)

    if (clauses.query[0] == "") {
      clauses.query = [
        "select fhir_type, fhir_id from resource_index_term where fhir_type = :type"
      ]
      clauses.params = [type: params.resource]
    }

    println "CLAUSE Q " + clauses.query + "||"
    println "CLAUSE P " + clauses.params

    def sorted = sortClauses(params, clauses)
    def unsorted = sortClauses([resource: params.resource], clauses)

    def countQuery  = """
					select count(distinct version_id) as count 
					from resource_index_term c where (fhir_type, fhir_id) in (${clauses.query[0]})
					"""

    println(countQuery)

    sorted.count = countQuery
    sorted.content = sorted.query[0] + " limit ${paging._count}" + " offset ${paging._skip}"
    sorted.uncontent = unsorted.query[0] + " limit ${paging._count}" + " offset ${paging._skip}"

    return sorted
  }

  private def includeProcessor(List<String> includes) {
    return { Resource r ->

      def d = fromResource(r)
      def includePathsAsXpath = includes.collect{ p->
        '//' + p.split('\\.').collect { "f:"+it }.join('/')
      }

      return includePathsAsXpath.collectMany { xp ->
        println ("Evaluating $xp against doc")
        xpathEvaluator.evaluate(xp, d, XPathConstants.NODESET).collect{
          xpathEvaluator.evaluate("./f:reference/@value",it)
        }.grep { it != null}
      }
    }
  }

  
  //TODO: support versioned references and determine access control rules
  public  Map<String,String> includesFor(Map params, Map<String,Resource> entries, Authorization a){

    List<Map> resourceIds = getResourceIds(params, entries)
    if (resourceIds.size() == 0) return null
    
    def vals = []
    Map p = [:]
    resourceIds.eachWithIndex { r, index ->
      vals += "(:fhir_type_${index}, :fhir_id_${index})"
      p["fhir_type_${index}"] = r.type
      p["fhir_id_${index}"] = r.id
    }
    
    String q = """select v.content, v.fhir_id, v.fhir_type, v.version_id from resource_version v where version_id in (
                    select max(version_id) from resource_version v where (fhir_type, fhir_id) in (""" + 
                      vals.join(",") + 
               """) group by fhir_id, fhir_type)""" 

    if (a.accessIsRestricted) {
      q  += "and (fhir_type, fhir_id) in (select fhir_type, fhir_id from resource_compartment where compartments  &&  ${a.compartmentsSql})"
    }
     
    return [
      content: q,
      params: p  
    ]

  }

  private List getResourceIds(Map params, Map entries) {
    List<String> includePaths = paramAsList(params._include)
    def includes = includeProcessor(includePaths)

    def resourcesToInclude = entries.collectMany {
      String uri, Resource r -> includes(r)
    } as Set

    List<Map> resourceIds = resourcesToInclude.collect {
      urlService.fhirUrlParts(it)
    }

    return resourceIds
  }

}
