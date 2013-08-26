package fhir
import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Resource

import com.mongodb.DBObject

class ApiController {
	
	static scope = "singleton"
	def searchIndexService
	def authorizationService
	
	
	def summary() {

		String compartment = authorizationService.compartmentFor(request)

		def q = [
			type:'DocumentReference',
			compartments:compartment,
			searchTerms: [
				$elemMatch: [
					k:'type:code', 
					v:'http://loinc.org/34133-9']]]

		List ids = ResourceIndex.collection
				.find(q, [latest:1])
				.collect {it.latest}

		DocumentReference doc = ResourceHistory.collection
				.find([_id: [$in:ids]])
				.sort([created:-1])
				.limit(1).first()
				.content.toString()
				.decodeFhirJson()

		def location = doc.locationSimple =~ /binary\/(.*)\/history\/(.*)\/raw/
		Binary b = ResourceHistory.getFhirVersion(location[0][1][1..-1], location[0][2][1..-1])
		params.raw = true
		request.resourceToRender =  b
	}

	def create() {
		params.id = new ObjectId().toString()
		update()
	}

	// TODO handle creating new rather than posting-with-name
	// extract logic to DRY from create()
	def update() {

		def body = request.getReader().text
		
		Resource r = request.withFormat {
			xml {body.decodeFhirXml()}
			json {body.decodeFhirJson()}
		}

		log.debug("Gonna encode" + r)
		DBObject rjson = r.encodeAsFhirJson().encodeAsDbObject()
		String type = rjson.keySet().iterator().next()
		String versionUrl;
		
		def indexTerms = searchIndexService.indexResource(r);
		def fhirId = params.id
	
		def compartments = params.list('compartments')
		
		def h = new ResourceHistory(
			fhirId: fhirId, 
			compartments: compartments,
			type: type,
			action: 'POST',
			content: rjson)
			.save()

		def rIndex = new ResourceIndex(
			fhirId: fhirId,
			compartments: compartments,
			latest: h.id,
			type: type,
			searchTerms: indexTerms.collect { it.toMap() })
			.save()
			
			
		versionUrl = g.createLink(
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
		def clauses = searchIndexService.queryParamsToMongo(params)
		log.debug(clauses.toString())
		
		def t0 = new Date().getTime()

		def query = clauses
		def MAX_COUNT = 100

		def incount = Integer.parseInt(params._count?:""+MAX_COUNT)
		def limit = Math.min(incount, MAX_COUNT)
		
		def inskip = Integer.parseInt(params._skip?:""+0)
		def skip = Math.max(inskip, 0)
		
		String type = searchIndexService.capitalizedModelName[params.resource]
		log.debug("Type: " + type)
		def cursor = ResourceIndex.collection.find([type: type]).skip(skip).limit(limit)
		int count = cursor.count()
		println("Count time: " + (new Date().getTime() - t0))
		
		def matches = []
		List ids = cursor.collect {
			it.latest
		}
		
		matches = ResourceHistory.collection.find(
			_id: [$in: ids]	
		).collect {
			it.content.toString().decodeFhirJson()
		}
		
		println("T: " + (new Date().getTime() - t0))	
	
		AtomFeed feed = new AtomFeed()
		feed.authorName = "groovy.config.atom.author-name"
		
		feed.authorUri  = "groovy.config.atom.author-uri"
		feed.id = request.forwardURI
		feed.totalResults = matches.size()
		if (skip + limit < count)
			feed.links.put("next", feed.id + "?_count=$limit&_skip=${skip+limit}")
		
		Calendar now = Calendar.instance
		feed.updated = now
			
		
		feed.entryList.addAll matches.collect { resource ->
			AtomEntry entry = new AtomEntry()
			entry.resource = resource
			entry.updated = now
			entry
		}
		
		request.resourceToRender = feed
	}

}
