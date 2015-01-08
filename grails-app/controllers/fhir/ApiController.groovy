package fhir

import java.util.regex.Pattern

import javax.sql.DataSource

import org.bson.types.ObjectId
import org.hibernate.SessionFactory
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.instance.model.Bundle.BundleType
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Bundle.BundleTypeEnumFactory;
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Patient
import org.hl7.fhir.instance.model.Resource

import fhir.searchParam.DateSearchParamHandler
import grails.transaction.Transactional


class ResourceDeletedException extends Exception {
  def ResourceDeletedException(String msg){
    super(msg)
  }
}

class ApiController {

  static scope = "singleton"
  SearchIndexService searchIndexService
  AuthorizationService authorizationService
  ConformanceService conformanceService
  SqlService sqlService
  BundleService bundleService
  UrlService urlService

  SessionFactory sessionFactory
  DataSource dataSource


  private List getRequestedCompartments(){
    params.list('compartments').collect{it}
  }

  def getFullRequestURI(){
    urlService.fullRequestUrl(request)
  }

  def welcome() {
    render(view:"/index")
  }

  def conformance(){
    request.resourceToRender = conformanceService.conformance
  }

  @Transactional
  def transaction() {

    def body = request.getReader().text
    Bundle feed = new Bundle()
    feed.type = BundleType.TRANSACTIONRESPONSE
    if (request.providingFormat == "json") {
      feed = body.decodeFhirJson()
    } else {
      feed = body.decodeFhirXml()
    }

    bundleService.validateFeed(feed)
    feed = bundleService.assignURIs(feed)

    feed.entry.each { BundleEntryComponent e->
      String r = searchIndexService.modelForClass(e.resource)
      log.debug("Transacting on an entry with $r / $e.resource.id")
      sqlService.updateResource(e.resource, r, e.resource.id, requestedCompartments, request.authorization)
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

    String versionUrl = sqlService.updateResource(r, params.resource, params.id, requestedCompartments, request.authorization)
    response.setHeader('Content-Location', versionUrl)
    response.setHeader('Location', versionUrl)
    response.setStatus(201)
    request.resourceToRender = r

  }

  def delete() {
    String fhirId = params.id

    // TODO DRY this logic with 'getOr404'... or pre-action
    // functions that handle this logic generically across
    // GET, PUT, DELETE...
    ResourceVersion h = sqlService.getLatestByFhirId(params.resource, fhirId)
    if (!h){
      response.status = 404
      return
    }
    sqlService.deleteResource(h, request.authorization)
    response.setStatus(204)
  }

  private void readService(ResourceVersion h) {

    if (!h){
      response.status = 404
      return
    }

    if (h.rest_operation == 'DELETE'){
      throw new ResourceDeletedException("Resource ${h.fhirId} has been deleted. Try fetching its history.")
    }

    request.authorization.assertAccessAny(operation:'GET', compartments: h.compartments)
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
  
  public Map<String,Resource> toEntryMap(Map sqlQuery) {
    def entries = sqlService.rows(sqlQuery.content, sqlQuery.params).collectEntries {
      [(it.fhir_type+'/'+it.fhir_id): it.content.decodeFhirJson()]
    }    
    return entries
  }

  def search(SearchCommand query) {
    request.t0 = new Date().time
    println "Binding some $params and some $request"

    time("precount")
    query.bind(params, request)
    def sqlQuery = query.clauses

    query.paging.total = sqlService.rows(sqlQuery.count, sqlQuery.params)[0].count
    def entries = toEntryMap(sqlQuery)
    time("got entries")
    
    def includes = query.includesFor(entries)
    if (includes) {
      entries += toEntryMap(includes)
    }

    Bundle feed = bundleService.createFeed([
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
      def deleted = it.rest_operation == "DELETE"
      [(it.fhir_type+'/'+it.fhir_id+'/_history/'+it.version_id):
	deleted? null : it.content.decodeFhirJson()]
    }
    time("got entries")

    time("Fetched content of size ${entries.size()}")

    Bundle feed = bundleService.createFeed([
      entries: entries,
      paging: paging,
      feedId: fullRequestURI
    ])

    time("Made feed")
    request.resourceToRender = feed
  }

}
