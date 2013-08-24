package fhir
import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.AtomEntry
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mongodb.DBObject

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
		String versionUrl;
		
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
		
		def cursor = ResourceIndex.collection.find([type: "DiagnosticOrder"]).skip(skip).limit(limit)
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
		
		//	matches.add ResourceHistory.getLatestByFhirId(it.fhirId.toString())	
	
		println("T: " + (new Date().getTime() - t0))	
//		Gson gson = new GsonBuilder().setPrettyPrinting().create();

//		def responseJson = '[' + matches.collect {
//				it.encodeAsFhirJson()
//		}.join(', ') + ']'
	
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
