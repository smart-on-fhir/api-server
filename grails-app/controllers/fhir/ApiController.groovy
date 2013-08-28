package fhir
import java.util.regex.Pattern;

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

	// Note that we don't offer real transaction (all-or-none) semantics
	// but we do allow clients to apply a group of changes in a single
	// request.  So it's  more like "batch" than a transaction...
	def transaction() {
		//TODO refactor
		def body = request.getReader().text

		def fhirBase = new URL(g.createLink(uri:'/fhir', absolute:true))
		AtomFeed f = request.withFormat {
			xml {body.decodeFhirXml()}
			json {body.decodeFhirJson()}
		}

		Map urlToFhirId = [:]
		Map urlsToAssign = [:]
		f.entryList.each { AtomEntry e -> 
			boolean needed = false
			Class c = e.resource.class
			try {
				def u = new URL(fhirBase, e.id)	
				// TODO extract into a "isFhirUrl" helper function 
				def asPath = u.path =~ /\/fhir\/([^\/]+)\/@([^\/]+)(?:\/history\/@([^\/]+))?/
				if(asPath) {
					urlToFhirId[e.id] = asPath[0][2] 
				}
				needed = !asPath
			} catch(Exception ex) {
				needed = true
			} finally {
				if (needed) urlsToAssign[e.id]=c
			}
		}

		Map assignments = urlsToAssign.collectEntries {from,c ->
			String r = c.toString().split('\\.')[-1].toLowerCase()
			println("Need to reassign URLs: " + from)
			String id = new ObjectId().toString()
			String to =  g.createLink(mapping:'resourceInstance', params: [resource:r, id:id]).replace("%40","@")[6..-1]
			urlToFhirId[to] = id
			return [(from):to]
		}

		def xml = f.encodeAsFhirXml()
		assignments.each {from,to ->
			xml = xml.replaceAll(Pattern.quote(from), to)
		}
		f = xml.decodeFhirXml()

		f.entryList.each { AtomEntry e ->
			String r = e.resource.class.toString().split('\\.')[-1].toLowerCase()
			updateService(e.resource, r, urlToFhirId[e.id])	
		}
		request.resourceToRender =  f
	}	
	
	def summary() {

		def compartments = authorizationService.compartmentsFor(request)
		log.debug("Compartments: " + compartments)

		def q = [
			type:'DocumentReference',
			compartments:[$in:compartments],
			searchTerms: [
				$elemMatch: [
					k:'type:code', 
					v:'http://loinc.org/34133-9']]]

		List ids = ResourceIndex.collection
				.find(q, [latest:-1])
				.collect {it.latest}

		def cursor = ResourceHistory.collection
				.find([_id: [$in:ids]])
				.sort([received:-1])
				.limit(1)

		if (cursor.count() == 0) {
			return response.status = 404
		}

		DocumentReference doc = cursor
				.first()
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

	def updateService(Resource r, String resourceName, String fhirId) {
		log.debug("Gonna encode" + r)
		DBObject rjson = r.encodeAsFhirJson().encodeAsDbObject()

		String type = rjson.keySet().iterator().next()
		String expectedType = searchIndexService.capitalizedModelName[resourceName]
		if (type != expectedType){
			response.status = 405
			log.debug("Got a request whose type didn't match: $expectedType vs. $type")
			return render("Can't post a $type to the $expectedType endpoint")
		}

		String versionUrl;
		
		def indexTerms = searchIndexService.indexResource(r);
	
		// TODO verify that any compartment passed in via URL params
		// matches the authorizationService.compartmentFor this request
		def compartments = params.list('compartments')
		if (!authorizationService.compartmentsAllowed(compartments, request)){
			response.status = 403
			return render("Can't write to compartment: $compartments")
		}
	
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
				resource: resourceName,
				id: fhirId,
				vid: h.id	
			]).replace("%40","@")
		
		log.debug("Created version: " + versionUrl)
		response.setHeader('Location', versionUrl)
		response.setStatus(201)
		request.resourceToRender = r
	}

	// TODO handle creating new rather than posting-with-name
	// extract logic to DRY from create()
	def update() {
		def body = request.getReader().text
		
		Resource r = request.withFormat {
			xml {body.decodeFhirXml()}
			json {body.decodeFhirJson()}
		}
		updateService(r, params.resource, params.id)
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
		log.debug("Count time: " + (new Date().getTime() - t0))
		
		def matches = []
		List ids = cursor.collect {
			it.latest
		}
		
		matches = ResourceHistory.collection.find(
			_id: [$in: ids]	
		).collect {
			it.content.toString().decodeFhirJson()
		}
		
		log.debug("T: " + (new Date().getTime() - t0))	
	
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
		request.t0 = t0
		log.debug("Feed after: " + (new Date().getTime() - t0))	
		
		request.resourceToRender = feed
	}

}
