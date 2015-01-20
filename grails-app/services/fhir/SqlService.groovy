package fhir;


import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.Patient
import org.hl7.fhir.instance.model.DomainResource
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.DocumentReference

import fhir.searchParam.IndexedValue
import fhir.AuthorizationService.Authorization
import groovy.sql.GroovyRowResult
import groovy.sql.Sql

import com.google.gson.JsonObject
import com.google.gson.JsonParser

class SqlService{

  def dataSource
  UrlService urlService
  AuthorizationService authorizationService
  SearchIndexService searchIndexService

  JsonParser jsonParser= new JsonParser()

  Sql getSql() {
    new Sql(dataSource)
  }

def generator = { String alphabet, int n ->
  new Random().with {
    (1..n).collect { alphabet[ nextInt( alphabet.length() ) ] }.join()
  }
}

  List<GroovyRowResult> rows(String q, Map params) {
    def label = generator( (('A'..'Z')+('0'..'9')).join(), 9 )
 
    //println("Params $params " + params.size())
    if (params.size() == 0) {
      return sql.rows(q)
    }
    def ret = sql.rows(q, params)
    println label + " q: " + q
    println label + " p: " + params
    println label + " s: " + ret.size()
    return ret
  }


  ResourceVersion getLatestByFhirId(String fhir_type, fhir_id) {
    return ResourceVersion.find("from ResourceVersion as v where v.fhir_type=? and v.fhir_id=? order by v.version_id desc", [fhir_type, fhir_id])
  }

  ResourceVersion getFhirVersion(String fhir_type, String fhir_id, Long version_id) {
    return ResourceVersion.find("from ResourceVersion as v where v.fhir_type=? and v.fhir_id=? and v.version_id=? order by v.version_id desc", [fhir_type, fhir_id, version_id])
  }

  Resource getLatestSummary(Authorization a) {

    def q = """select min(content) as content from resource_version where (fhir_type, fhir_id) in (
						select fhir_type, fhir_id from resource_compartment where fhir_type='DocumentReference'
						and compartments && """ +a.compartmentsSql+"""
					) group by fhir_type, fhir_id order by max(version_id) desc limit 1"""
    def content = rows(q, [:])
    if (!content) return null

    DocumentReference r = content[0].content.decodeFhirJson()
    Map location = urlService.fhirUrlParts(r.locationSimple)
    return getLatestByFhirId(location.type, location.id).content.decodeFhirJson()
  }

  void insertIndexTerms(List inserts, versionId, String fhirType, String fhirId, Resource r) {
    def indexTerms = searchIndexService.indexResource(r);

    indexTerms.collect { IndexedValue val ->
      val.handler.createIndex(val, versionId, fhirId, fhirType)
    }.each { ResourceIndexTerm term ->
      inserts.add(term.insertStatement(versionId))
    }

    if (r instanceof DomainResource) {
        r.contained.each { Resource it ->
          insertIndexTerms(inserts, 0, it.class.name.split("\\.")[-1], fhirId+"_contained_"+it.id, it)
        }
    }
    return
  }

  /**
   * @param r 		Resource or ResourceVersion to inspect for compartments
   * @param fhirId	ID of the Resource (only required if it's a Patient resource)	
   * @param compartments List of compartments that must to check for authorization
   * @return			List of all compartments needed
   * @throws			AuthorizatinException if user doesn't have access to _all_ compartments
   */
  private List<String> compartmentsForResource(r, String fhirId) {
    def compartments = []

    log.debug("Authorizing compartments start from: $compartments")

    if (r instanceof Patient) {
      compartments.add("Patient/$fhirId")
    } else if ("subject" in r.properties) {
      compartments.add(r.subject.reference)
    } else if ("patient" in r.properties) {
      compartments.add(r.patient.reference)
    }

    return compartments
  }

  private def deleteResource(ResourceVersion h, authorization) {
    authorization.assertAccessAny(operation:'DELETE', compartments:h.compartments)

    String fhirType = h.fhir_type

    Map dParams = [
      fhir_id: h.fhir_id,
      fhir_type: h.fhir_type,
      rest_operation: 'DELETE',
      content: 'deleted'
    ]
    log.debug("Deleting $dParams")
    ResourceVersion deleteEntry = new ResourceVersion(dParams)
    deleteEntry.save(failOnError: true)

    log.debug("Deleted $deleteEntry")
    sql.execute("delete from resource_index_term where fhir_type=:type and fhir_id=:id", [
      type: h.fhir_type,
      id: h.fhir_id
    ])

    log.debug("Deleted resource: " + h.fhir_id)
  }


  public def updateResource(Resource r, String resourceName, String fhirId, List needCompartments, authorization) {

    List compartments = needCompartments + compartmentsForResource(r, fhirId)
    authorization.assertAccessEvery(compartments: compartments)
    
    def h = new ResourceVersion(
        fhir_id: fhirId,
        fhir_type: resourceName,
        rest_operation: 'POST',
        content: "placeholder")
    h.save(failOnError: true)
    
    r.meta.setVersionId(h.version_id.toString())
    r.meta.setLastUpdated(new Date())
    
    JsonObject rjson = jsonParser.parse(r.encodeAsFhirJson())

    log.debug("Updating to version id: ${h.fhir_id} /_history/ " + h.version_id)
    log.debug("raw " + rjson)
    log.debug("Parsed a $rjson.resourceType.asString")

    String fhirType = rjson.resourceType.asString
    String expectedType = resourceName
    
    if (fhirType != expectedType){
      log.debug("Got a request whose type didn't match: $expectedType vs. $fhirType")
      throw new Exception("Can't post a $fhirType to the $expectedType endpoint");
    }
    
    if (fhirId != r.id){
      log.debug("Got a resource to create ids type didn't match: $r.id vs. $fhirId")
      throw new Exception("Can't post a $fhirType with id $fhirId when content id is $r.id");
    }
    
    h.content = rjson.toString()
    h.save(failOnError: true)

    String versionUrl

    def inserts = []

    // remove indexing from contained resources
    inserts.add ("""delete from resource_index_term where (fhir_type, fhir_id) in 
                 (select reference_type, reference_id from resource_index_term where
                 fhir_type='$fhirType' and fhir_id='$fhirId' and reference_id like '%_contained_%');""")

    // remove indexing from the resoure
    inserts.add ("delete from resource_index_term where fhir_type=$fhirType and fhir_id=$fhirId;" )

    inserts.add ("delete from resource_compartment where fhir_type= $fhirType and fhir_id= $fhirId;" )
    inserts.add("insert into resource_compartment (fhir_type, fhir_id, compartments) values ($fhirType, $fhirId, '{" +compartments.join(",")+"}');")
    insertIndexTerms(inserts, h.version_id, fhirType, fhirId, r)

    inserts.each {println it; sql.execute(it) }

    versionUrl = urlService.resourceVersionLink(resourceName, fhirId, h.version_id)
    log.debug("Created version: " + versionUrl)
    return versionUrl

  }

}
