package fhir

import java.util.regex.Pattern
import javax.sql.DataSource
import java.lang.*

import org.bson.types.ObjectId
import org.hibernate.SessionFactory
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Patient
import org.hl7.fhir.instance.model.Resource

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mongodb.BasicDBObject

import fhir.searchParam.DateSearchParamHandler
import grails.transaction.Transactional

class ApiControllerCommands {}

@grails.validation.Validateable(nullable=true)
class PagingCommand {
  Integer total
  Integer _count
  Integer _skip

  def bind(params, request) {
    _count = params._count ? Math.min(Integer.parseInt(params._count), 50) : 50
    _skip = params._skip ? Integer.parseInt(params._skip) : 0
  }
}

@grails.validation.Validateable(nullable=true)
class HistoryCommand {
  Date _since
  Map clauses = [:]
  def request
  def searchIndexService
  def fhirType
  def fhirId

  //TODO restrict history by compartment

  def getClauses() {
    if (request == null) {
      return null;
    }
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

@grails.validation.Validateable(nullable=true)
class SearchCommand {
  def params
  def request
  def searchIndexService
  PagingCommand paging

  def getClauses() {
    if (params == null  || request == null) {
      return null;
    }

    def clauses = searchIndexService.searchParamsToSql(params, request.authorization, paging)
    return clauses
  }
  
  Map<String,String> includesFor(Collection<BundleEntryComponent> entries){
    def ret = searchIndexService.includesFor(params, entries, request.authorization)
    return ret
  }

  def bind(params, request) {
    this.params = params
    this.request = request
    this.paging = new PagingCommand()
    this.paging.bind(params, request)
  }
}

