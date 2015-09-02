package fhir;

import java.util.regex.Pattern

import javax.annotation.PostConstruct
import javax.xml.xpath.XPathConstants

import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.instance.model.Conformance.ConformanceRestComponent
import org.hl7.fhir.instance.model.Enumerations.SearchParamType

import fhir.AuthorizationService.Authorization
import fhir.searchParam.DateSearchParamHandler
import fhir.searchParam.IdSearchParamHandler
import fhir.searchParam.IndexedValue
import fhir.searchParam.SearchParamHandler
import fhir.searchParam.SearchedValue
import fhir.searchParam.StringSearchParamHandler

/**
 * @author jmandel
 *
 */
class SearchIndexService{

  static def transactional = false
  static def lazyInit = false

  static UrlService urlService
  static ConformanceService conformanceService
  static XmlService xmlService

  static Map<Class<Resource>,Collection> indexersByResource = [:]
  static IdSearchParamHandler idIndexer;

  @PostConstruct
  void init() {

    xmlService.configureXpathSettings();
    SearchParamHandler.injectXpathEvaluator(xmlService.xpathEvaluator)
    SearchParamHandler.injectUrlService(urlService)
    conformanceService.generateConformance()

    // For each searchParam we support, create a SearchParamHandler instance
    // to handle indexing newly POSTed resources, and searching for existing ones
    conformanceService.conformance.rest[0].resource.each { resource ->
      Class model = classForModel resource.type

      String resourceName = resource.type
      indexersByResource[model] = resource.searchParam.collect { searchParam ->


        // join search param declaration in Conformance against search param definition
        // in individual profiles. This is only necessary to extract the xpath...
        // (Since all other properties of the searchParams are repeated in Conformance)
        String paramName = searchParam.name
        String key = resourceName + ':' + paramName

        // Short-circuit FHIR's built-in xpath if defined. Handles:
        //  * missing xpaths
        //  * broken xpaths  -- like 'f:value[x]'
        SearchParamHandler.create(
            searchParam.name,
            searchParam.type,
            resource.type,
            conformanceService.searchParamXpaths[key],
            conformanceService.searchParamReferenceTypes[key]);
      } +
      new IdSearchParamHandler( searchParamName: "_id",
        fieldType: SearchParamType.TOKEN,
        xpath: null,
        resourceName: resourceName) +
      new DateSearchParamHandler( searchParamName: "_lastUpdated",
        fieldType: SearchParamType.DATE,
        xpath: "f:meta/f:lastUpdated",
        resourceName: resourceName) +
      new StringSearchParamHandler(searchParamName: "_profile",
        fieldType: SearchParamType.STRING,
        xpath: "f:meta/f:profile",
        resourceName: resourceName);
    }
  }

  public String modelForClass(Resource r) {
    return r.class.toString().split('\\.')[-1].replace("_", "")
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

  private static Class lookupClass(String name){
    Class.forName(name,	true, classLoader)
  }

  private static ClassLoader getClassLoader(){
    Thread.currentThread().contextClassLoader
  }

  public List<IndexedValue> indexResource(Resource rx) {

    log.info("\n\nExtracting search index terms for a new " + rx.class)

    Collection indexers = indexersByResource[rx.class]
    if (!indexers){
      return []
    }

    org.w3c.dom.Document rdoc = xmlService.fromResource(rx)
    def ret = ((Iterable) indexers
        .findAll({ ! (it instanceof IdSearchParamHandler )}))
        .collectMany({ SearchParamHandler h -> h.execute(rdoc) })

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
    List ret
    if  (v == null || v == "") ret = []
    else if (v.class == null) ret = []
    else if (v instanceof String[]) ret = v as List
    else ret = [v]

    if (ret && ret.size()>0 && ret[0] == "") ret = []
    return ret
  }


  // Split on the first occurrence of a sting.
  // @example splitOne("a.bc.defg", ".") --> ["a", "bc.defg"]
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


  /**
   * @param params
   * @return
   * 
   * Take a raw Grails query params map, and returns a tree of SearchedValues.
   * Each SearchedValue has a SearchParamHandler, modifier, and value. So for example, a
   * query that looked like:
   *    /Patient?identifier=123,456
   *    
   * would return a list with a single SearchedValue like:
   *    handler={the TokenSearchParamHandler for Patient.identifier}, 
   *    modifier=null
   *    values="123,456"
   *   
   * The fun begins with chained queries. In this case, the results includes
   * a sub-list for each chained parameter. The sub-list is preceded by an unbound
   * match for the reference search param.
   * 
   * For example a query like
   *    /Condition?subject:Patient.identifer=123
   *    
   * would return a SearchedValue like:
   *    handler={ReferenceSearchParamHandler for Condition.subject}
   *    modifier=null,
   *    values=null
   *    chained=SearchedValue(
   *       handler=(TokenSearchParamHandler for for Patient.identifier)
   *       modifier=null,
   *       values="123"
   *    )
   */
  public List<SearchedValue> queryToHandlerTree (Map params) {
    List<SearchedValue> ret = []
    Map indexerFor = indexerFor(params.resource)

    // Represent each term in the query a
    // key, modifier, and value
    // e.g. [key: "date", modifier: "after", value: "2010"]
    params.collectEntries { String searchParam, searchValues ->
      [(searchParam): paramAsList(searchValues)]
    }.findAll{ searchParam, searchValues ->
      searchValues.size()>0
    }.sort{ a,b ->
      b.key.length() <=> a.key.length()
    }.each { String searchParam, searchValues ->

      def (isChained, beforeChain, afterChain) = chainParts(searchParam)
      def (paramName, modifier) = paramParts(beforeChain)

      SearchParamHandler indexer = indexerFor[paramName]
      if (!indexer){ return }

      searchValues.each { String searchValue ->
        def chainedSearchValue = null
        if (isChained) { // a chaining query -- need to recurse
          def chainedResource = modifier
          if (!chainedResource) {
            if (indexer.referenceTypes.size() == 1) {
              chainedResource = indexer.referenceTypes[0]
            }
            else {
              throw new Exception("The search param '$paramName' can match ${indexer.referenceTypes.size()} types. Please specify further by providing one of: " +
              indexer.referenceTypes.collect {"$paramName:$it="}.join(" or "));
            }
          }
          // infer type if there is no ambiguity
          chainedSearchValue = queryToHandlerTree([resource: chainedResource, (afterChain): searchValue])[0]
          searchValue = null
        }
        ret += new SearchedValue(
            handler: indexer,
            modifier: modifier,
            values: searchValue,
            chained: chainedSearchValue)
      }
    }

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

  def joinClauses(List<SearchedValue> clauseTree, String resourceName, Authorization a) {
    joinClauses(clauseTree, resourceName, a, 0)
  }
  def joinClauses(List<SearchedValue> clauseTree, String resourceName, Authorization a, clauseNum) {

    List<String> query = []
    Map params = [:]

    clauseTree.each { clause ->
      clauseNum++

      println("Doing $resourceName, $clause $clauseNum")
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
      query +=  """ 
          SELECT fhir_type, fhir_id 
          from resource_index_term where
          fhir_type = :type_${clauseNum} """ +
          (clause.handler instanceof IdSearchParamHandler ? "" :
          " AND search_param = :field_${clauseNum} ") +
          (orClauses.size() == 0 ? "" :
          " AND (" + orClauses.join(" OR \n") +")")


      if (clause.chained) {
        clauseNum++
        Map subClauses = joinClauses([clause.chained], null, a, clauseNum)
        query[-1] += " AND (reference_type, reference_id) in (\n" + subClauses.query + "\n)"
        params += subClauses.params
        clauseNum += subClauses.params.size()
      }

    }

    if (query.size() == 0) {
      clauseNum++;
      query  += " select fhir_type, fhir_id from resource_index_term where fhir_type = :type_${clauseNum} "
      params += [('type_'+clauseNum): resourceName]
    }

    if (a.accessIsRestricted && resourceName != null) {
      //TODO remove resourceName != null restriction to enforce compartment permissions on joined resources
      query  += "select fhir_type, fhir_id from resource_compartment where (compartments  &&  ${a.compartmentsSql}) or compartments = '{}'"
    }

    return [query: query.join("\nINTERSECT\n"), params: params]
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
    (orderBy.collect{Map o -> """ (
        select ${o.sortDirection.fn}(${o.column})
        from resource_index_term t where 
           t.search_param=:${o.searchParamName} and 
           t.fhir_type=s.fhir_type and 
           t.fhir_id=s.fhir_id
        ) ${o.sortDirection.dir} """ } +
    " s.version_id asc "
    ).join(", ")
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
      query: """
               select v.content, v.fhir_id, v.fhir_Type, v.version_id 
               from resource_Version v join (
                 SELECT s.version_id, row_number() over (ORDER BY ${fullOrderClause(orderBy)} ) as sortv
                 from resource_index_term s 
                   where (s.fhir_type, s.fhir_id) in ( ${cs.query} )
                     GROUP BY s.version_id, s.fhir_type, s.fhir_id
                     ORDER BY  ${fullOrderClause(orderBy)} 
               ) o on v.version_id=o.version_id
               order by sortv """,
      params: cs.params
    ]
  }

  public Map<String,String> searchParamsToSql(Map params, Authorization a, paging){

    List<SearchedValue> clauseTree = queryToHandlerTree(params)
    def clauses = joinClauses(clauseTree, params.resource, a)

    println "CLAUSE Q " + clauses.query
    println "CLAUSE P " + clauses.params

    def sorted = sortClauses(params, clauses)

    def countQuery  = """
        select count(distinct version_id) as count 
        from resource_index_term c
        where (fhir_type, fhir_id) in (${clauses.query}) """

    println(countQuery)

    sorted.count = countQuery
    sorted.content = sorted.query + " limit ${paging._count}" + " offset ${paging._skip}"

    return sorted
  }

  // Helper function to evaluate _include terms against a page of results
  // for example, GET /List?_include=List.entry.item
  // should generate a list of required items for inclusion on a result page
  //TODO: support versioned references
  public  Map<String,String> includesFor(Map params, Collection<BundleEntryComponent> entries, Authorization a){

    List<Map> resourceIds = getResourceIds(params, entries)
    if (resourceIds.size() == 0) return null

    def vals = []
    Map p = [:]
    resourceIds.eachWithIndex { r, index ->
      vals += "(:fhir_type_${index}, :fhir_id_${index})"
      p["fhir_type_${index}"] = r.type
      p["fhir_id_${index}"] = r.id
    }

    String q = """
      select v.content, v.fhir_id, v.fhir_type, v.version_id
      from resource_version v where version_id in (
          select max(version_id) 
            from resource_version v where (fhir_type, fhir_id) in (""" +
        vals.join(",") + """
            ) group by fhir_id, fhir_type
      )"""

    if (a.accessIsRestricted) {
      q  += """
        and (fhir_type, fhir_id) in (
          select fhir_type, fhir_id 
          from resource_compartment
          where (compartments  &&  ${a.compartmentsSql}) or compartments = '{}'
        )"""
    }

    return [
      content: q,
      params: p
    ]
  }

  private def includeProcessor(List<String> includes) {
    return { Resource r ->

      def d = xmlService.fromResource(r)
      def includePathsAsXpath = includes.collect{ p->
        '//' + conformanceService.searchParamXpaths[p]
      }

      return includePathsAsXpath.collectMany { xp ->
        xmlService.xpathEvaluator.evaluate(xp, d, XPathConstants.NODESET).collect{
          xmlService.xpathEvaluator.evaluate("./f:reference/@value",it)
        }.grep { it != null}
      }
    }
  }

  private List getResourceIds(Map params, Collection<BundleEntryComponent> entries) {
    def includes = includeProcessor(paramAsList(params._include))

    def resourcesToInclude = entries.collectMany { BundleEntryComponent c ->
      includes(c.resource)
    } as Set

    return resourcesToInclude.collect { urlService.fhirUrlParts(it) }
  }

}
