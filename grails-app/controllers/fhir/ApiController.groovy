package fhir
import org.bson.types.ObjectId
import org.hl7.fhir.instance.formats.JsonComposer
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Resource

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import com.mongodb.util.JSON

import fhir.searchParam.SearchParamHandler
import fhir.searchParam.SearchParamValue

import java.io.InputStream

class ApiController {
	
	static scope = "singleton"
	def static searchIndexService
	
	def create() {

		def body = request.getReader().text
		
		Resource r = request.withFormat {
			xml {body.decodeFhirXml()}
			json {body.decodeFhirJson()}
		}
		
		DBObject rjson = r.encodeAsFhirJson().encodeAsDbObject()
		String type = rjson.keySet().iterator().next()
		
		def indexTerms = searchIndexService.indexResource(r);
		def fhirId = new ObjectId()
		
		def h = new ResourceHistory(
			fhirId: fhirId, 
			type: type,
			action: 'POST',
			content: rjson)
			.save()
		
		def rIndex = new ResourceIndex(
			fhirId: fhirId,
			type: type,
			searchTerms: indexTerms.collect { it.toMap() })
			.save()
			
		String versionUrl = g.createLink(
			mapping: 'resourceVersion',
			absolute: true,
			params: [
				resource: params.resource,
				id: fhirId,
				vid: h.id	
			]).replace("%40","@")
		
		
		log.debug("Created version: " + versionUrl)
		response.setHeader('Location', versionUrl)
		response.setStatus(201)
		request.resourceToRender = r
	}

	def read() {
		Resource r = ResourceHistory.getLatestByFhirId(params.id)
		
		if (!r){
			return response.status = 404
		} 

		request.resourceToRender = r
	}	

	def vread() {
		Resource r = ResourceHistory.getFhirVersion(params.id, params.vid)
		
		if (!r){
			return response.status = 404
		}
		request.resourceToRender = r
	}

	def search() {
		
		def rc = searchIndexService.classForModel(params.resource)
		def indexers = searchIndexService.indexersByResource[rc]
		
		def byParam = indexers.collectEntries { 
			[(it.fieldName): it]
		}
				
		def searchParams = params.collect {k,v ->
			def c = k.split(":") as List
			[
			  key: c[0],
			  modifier: c[1],
			  value: v
			]
		  }.findAll {
		  	 it.key in byParam
		  }
		  
		List<BasicDBObject> clauses = []
		  
		searchParams.each { p->
			log.debug("Indexing $p with " + byParam[p.key])
			clauses.add(byParam[p.key].searchClause(p))
		}
		log.debug(SearchParamHandler.and(clauses).toString())
		response.setHeader('Content-type', "text/json")
		def query = SearchParamHandler.and(clauses)
		def matches = ResourceIndex.collection.find(query).toList()		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		def responseJson = '[' + matches.collect {
				ResourceHistory.getLatestByFhirId(it.fhirId.toString())
				.encodeAsFhirJson()
		}.join(', ') + ']'
				
		render(responseJson)
		log.debug(gson.toJson(query))
	}

}
