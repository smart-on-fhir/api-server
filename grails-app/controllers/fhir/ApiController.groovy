package fhir

import java.util.regex.Pattern
import javax.sql.DataSource

import org.bson.types.ObjectId
import org.hibernate.SessionFactory
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Patient
import org.hl7.fhir.instance.model.Resource

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mongodb.BasicDBObject

import fhir.searchParam.DateSearchParamHandler
import fhir.searchParam.IndexedValue
import grails.transaction.Transactional

class PagingCommand {
  Integer total
  Integer _count
  Integer _skip

  def bind(params, request) {
    _count = params._count ? Math.min(Integer.parseInt(params._count), 50) : 50
    _skip = params._skip ? Integer.parseInt(params._skip) : 0
  }
}

class HistoryCommand {
  Date _since
  Map clauses = [:]
  def request
  def searchIndexService
  def fhirType
  def fhirId

  //TODO restrict history by compartment

  def getClauses() {
    Map params = [:]
    List restrictions = []

    if (_since != null) {
      restrictions += "rest_date > :_since"
      params += [_since:_since]
    }
    if (fhirType != null) {
      restrictions += "fhir_type= :fhirType"
      params += [fhirType:fhirType]
    }
    if (fhirId != null) {
      restrictions += "fhir_id = :fhirId"
      params += [fhirId:fhirId]
    }
    if (request.authorization.accessIsRestricted) {
      restrictions += " exists (select fhir_type, fhir_id from resource_compartment c " +
          " where v.fhir_type=c.fhir_type and v.fhir_id=c.fhir_id and " +
          " compartments &&   ${request.authorization.compartmentsSql} )"
      params += [fhirId:fhirId]
    }

    def restrictionsString = ""
    if (restrictions.size() > 0)
      restrictionsString = " WHERE " + restrictions.join(" AND ")

    return [
      count: "select count(*) from resource_version v $restrictionsString",
      content: "select * from resource_version v $restrictionsString",
      params:  params
    ]
  }

  def bind(params, request) {
    this.request = request
    this._since = params._since ? new java.sql.Date(DateSearchParamHandler.precisionInterval(params._since).start.toDate().time) : null
    this.fhirType = params.resource ?: null
    this.fhirId = params.id ?: null
  }
}

class SearchCommand {
  def params
  def request
  def searchIndexService
  PagingCommand paging

  def getClauses() {
    def clauses = searchIndexService.searchParamsToSql(params, request.authorization, paging)
    return clauses
  }

  def bind(params, request) {
    this.params = params
    this.request = request
    this.paging = new PagingCommand()
    this.paging.bind(params, request)
  }
}

class ResourceDeletedException extends Exception {
  def ResourceDeletedException(String msg){
    super(msg)
  }
}

class ApiController {

  static scope = "singleton"
  def searchIndexService
  def authorizationService
  SqlService sqlService

  BundleService bundleService
  UrlService urlService
  JsonParser jsonParser= new JsonParser()

  SessionFactory sessionFactory
  DataSource dataSource

  def getFullRequestURI(){
    urlService.fullRequestUrl(request)
  }

  def welcome() {
    render(view:"/index")
  }

  def conformance(){
    request.resourceToRender = searchIndexService.conformance
  }

  @Transactional
  def transaction() {

    def body = request.getReader().text
    AtomFeed feed;
    if (request.providingFormat == "json") {
      feed = body.decodeFhirJson()
    } else {
      feed = body.decodeFhirXml()
    }

    bundleService.validateFeed(feed)
    feed = bundleService.assignURIs(feed)

    feed.entryList.eachWithIndex { AtomEntry e, int i->
      String r = e.resource.class.toString().split('\\.')[-1].replace("_", "")
      updateService(e.resource, r, e.id.split('/')[1])
    }

    request.resourceToRender =  feed
  }

  // BlueButton-specific "summary" API: returns the most recent C-CDA
  // clinical summary (resolving DocumentReference -- Document)
  def summary() {

    log.debug("Compartments: " + request.authorization.compartments)
    def d = sqlService.getLatestSummary(request.authorization)

    if (d == null) {
      return response.status = 404
    }

    request.resourceToRender = d
  }

  def create() {
    params.id = new ObjectId().toString()
    update()
  }

  private def updateService(Resource r, String resourceName, String fhirId) {

    def compartments = getAndAuthorizeCompartments(r, fhirId)
    log.debug("Compartments: $compartments")

    JsonObject rjson = jsonParser.parse(r.encodeAsFhirJson())

    log.debug("raw " + rjson)
    log.debug("Parsed a $rjson.resourceType.asString")

    String fhirType = rjson.resourceType.asString
    String expectedType = resourceName

    if (fhirType != expectedType){
      response.status = 405
      log.debug("Got a request whose type didn't match: $expectedType vs. $fhirType")
      return render("Can't post a $fhirType to the $expectedType endpoint")
    }

    String versionUrl;

    def h = new ResourceVersion(
        fhir_id: fhirId,
        fhir_type: fhirType,
        rest_operation: 'POST',
        content: rjson.toString())
    h.save(failOnError: true)

    def inserts = []

    inserts.add ("delete from resource_compartment where fhir_type= $fhirType and fhir_id= $fhirId;" )
    inserts.add("insert into resource_compartment (fhir_type, fhir_id, compartments) values ($fhirType, $fhirId, '{" +compartments.join(",")+"}');")
    insertIndexTerms(inserts, h.version_id, fhirType, fhirId, r)

    inserts.each { sqlService.sql.execute(it) }

    versionUrl = urlService.resourceVersionLink(resourceName, fhirId, h.version_id)
    log.debug("Created version: " + versionUrl)

    response.setHeader('Content-Location', versionUrl)
    response.setHeader('Location', versionUrl)
    response.setStatus(201)
    request.resourceToRender = r
  }

  def insertIndexTerms(List inserts, versionId, String fhirType, String fhirId, Resource r) {
    def indexTerms = searchIndexService.indexResource(r);

    indexTerms.collect { IndexedValue val ->
      val.handler.createIndex(val, versionId, fhirId, fhirType)
    }.each { ResourceIndexTerm term ->
      inserts.add(term.insertStatement(versionId))
    }

    r.contained.each { Resource it ->
      insertIndexTerms(inserts, 0, it.class.name.split("\\.")[-1], fhirId+"_contained_"+it.xmlId, it)
    }
    return
  }

  def update() {
    def body = request.reader.text
    Resource r;

    if (params.resource == 'Binary')  {
      r = new Binary();
      r.setContentType(request.contentType)
      r.setContent(body.bytes)
    } else {
      if (request.providingFormat == "json") {
        r = body.decodeFhirJson()
      } else {
        r = body.decodeFhirXml()
      }
    }

    updateService(r, params.resource, params.id)
  }


  /**
   * @param r 		Resource or ResourceVersion to inspect for compartments
   * @param fhirId	ID of the Resource (only required if it's a Patient resource)	
   * @return			List of all compartments needed
   * @throws			AuthorizatinException if user doesn't have access to _all_ compartments
   */
  private List<String> getAndAuthorizeCompartments(r, String fhirId) {
    def compartments = params.list('compartments').collect {it}
    compartments += request.authorization.compartments
    compartments = compartments as Set

    log.debug("Authorizing comparemtns start from: $compartments")

    if (r instanceof Patient) {
      compartments.add("Patient/$fhirId")
    } else if ("subject" in r.properties) {
      compartments.add(r.subject.referenceSimple)
    } else if ("patient" in r.properties) {
      compartments.add(r.patient.referenceSimple)
    }

    if (!request.authorization.allows(operation: "PUT", compartments: compartments)){
      throw new AuthorizationException("Can't write to compartments: $compartments")
    }

    return compartments as List
  }

  private def deleteService(ResourceVersion h, String fhirId) {
    List<String> compartments = getAndAuthorizeCompartments(h, fhirId);
    log.debug("Compartments: $compartments")
    String fhirType = h.fhirType


    Map dParams = [
      fhirId: fhirId,
      compartments: compartments as String[],
      fhirType: h.fhirType,
      action: 'DELETE',
      content: null
    ]
    log.debug("Deleting $dParams")

    ResourceVersion deleteEntry = new ResourceVersion(dParams).save()

    log.debug("Deleted $deleteEntry")

    ResourceIndex.forResource(fhirType).remove(new BasicDBObject([
      fhirId: fhirId,
      fhirType: fhirType
    ]))

    log.debug("Deleted resource: " + fhirId)
    response.setStatus(204)
  }

  def delete() {
    String fhirId = params.id

    // TODO DRY this logic with 'getOr404'... or pre-action
    // functions that handle this logic generically across
    // GET, PUT, DELETE...
    ResourceVersion h = ResourceVersion.getLatestByFhirId(fhirId)
    if (!h){
      response.status = 404
      return
    }
    request.authorization.require(operation:'DELETE', resource:h)
    deleteService(h, fhirId)
  }

  private void readService(ResourceVersion h) {

    if (!h){
      response.status = 404
      return
    }

    if (h.rest_operation == 'DELETE'){
      throw new ResourceDeletedException("Resource ${h.fhirId} has been deleted. Try fetching its history.")
    }

    request.authorization.require(operation:'GET', resource:h)
    log.debug("K, ,authorized")
    request.resourceToRender = h
  }

  def read() {
    ResourceVersion h = sqlService.getLatestByFhirId(params.resource, params.id)
    readService(h)
  }

  def vread() {
    ResourceVersion h = sqlService.getFhirVersion(params.resource, params.id, Long.parseLong(params.vid))
    readService(h)
  }

  def time(label) {
    log.debug("T $label: " + (new Date().getTime() - request.t0))
  }

  def search(SearchCommand query) {
    request.t0 = new Date().time
    query.bind(params, request)

    def sqlQuery = query.clauses
    time("precount")

    query.paging.total = sqlService.rows(sqlQuery.count, sqlQuery.params)[0].count
    def entries = sqlService.rows(sqlQuery.content, sqlQuery.params).collectEntries {
      [(it.fhir_type+'/'+it.fhir_id): it.content.decodeFhirJson()]
    }
    time("got entries")

    AtomFeed feed = bundleService.atomFeed([
      entries: entries,
      paging: query.paging,
      feedId: fullRequestURI
    ])

    time("Made feed")
    request.resourceToRender = feed
  }

  def history(PagingCommand paging, HistoryCommand history) {

    history.bind(params, request)
    paging.bind(params, request)

    def clauses = history.clauses

    log.debug("history query: " + clauses.count)

    println sqlService.rows(clauses.count, clauses.params)
    paging.total = sqlService.rows(clauses.count, clauses.params)[0].count
    time("Counted $paging.total tosort ${params.sort}")

    def rawSqlQuery = clauses.content +
        " limit ${paging._count}" +
        " offset ${paging._skip}"

    def entries = sqlService.rows(rawSqlQuery, clauses.params).collectEntries {
      [(it.fhir_type+'/'+it.fhir_id+'/_history/'+it.version_id): it.content.decodeFhirJson()]
    }
    time("got entries")

    time("Fetched content of size ${entries.size()}")

    AtomFeed feed = bundleService.atomFeed([
      entries: entries,
      paging: paging,
      feedId: fullRequestURI
    ])

    time("Made feed")
    request.resourceToRender = feed
  }

}
