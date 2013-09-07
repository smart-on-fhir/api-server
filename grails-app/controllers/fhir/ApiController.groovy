package fhir

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Resource

import com.mongodb.DBApiLayer
import com.mongodb.DBObject


class PagingCommand {
    Integer total
    Integer _count
	Integer _skip
	
	def bind(params, request) {
		_count = params._count ? Math.min(Integer.parseInt(params._count), 50) : 50
		_skip = params._skip ? Integer.parseInt(params._skip) : 0
    }
}

class SearchCommand {

	def params
	def request
	def searchIndexService
	
	def getClauses() {
		def clauses = searchIndexService.queryParamsToMongo(params)
		return request.authorization.restrictSearch(clauses)
	}

	def bind(params, request) {
		this.params = params
		this.request = request
	}
}

class ApiController {
	
	static scope = "singleton"
	def searchIndexService
	def authorizationService
	BundleService bundleService

	def getFullRequestURI(){
		log.debug("from ${bundleService.domain} [/] ${request.forwardURI}")
		bundleService.domain + request.forwardURI + '?' + request.queryString
	}

	// Note that we don't offer real transaction (all-or-none) semantics
	// but we do allow clients to apply a group of changes in a single
	// request.  So it's  more like "batch" than a transaction...
	def transaction() {

		def body = request.getReader().text
		AtomFeed feed = request.withFormat {
			xml {body.decodeFhirXml()}
			json {body.decodeFhirJson()}
		}
		
		bundleService.validateFeed(feed)


		feed = bundleService.assignURIs(feed)

		feed.entryList.each { AtomEntry e ->
			String r = e.resource.class.toString().split('\\.')[-1].toLowerCase()
			updateService(e.resource, r, e.id.split('@')[1])	
		}	

		request.resourceToRender =  feed
	}	
	
	def summary() {

		log.debug("Compartments: " + request.authorization.compartments)

		def q = [
			type:'DocumentReference',
			compartments:[$in:request.authorization.compartments],
			searchTerms: [ $elemMatch: [
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

		def location = doc.locationSimple =~ /binary\/@(.*)/
		request.resourceToRender = ResourceHistory.getLatestByFhirId(location[0][1])
	}

	def create() {
		params.id = new ObjectId().toString()
		update()
	}

	private def updateService(Resource r, String resourceName, String fhirId) {

		def compartments = params.list('compartments')
		if (!request.authorization.allows(operation: "PUT", compartments: compartments)){
			throw new AuthorizationException("Can't write to compartments: $compartments")
		}

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
	

		def h = new ResourceHistory(
			fhirId: fhirId, 
			compartments: compartments,
			type: type,
			action: 'POST',
			content: rjson)
			.save()
			
		ResourceIndex.collection.remove([
				fhirId: fhirId,
				type: type
		])

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
	
	private void readService(ResourceHistory h) {
		if (!h){
			response.status = 404
			return
		} 
		request.authorization.require(operation:'GET', resource:h)
		log.debug("K, ,authorized"+ h.properties)
		request.resourceToRender = h		
	}

	def read() {
		ResourceHistory h = ResourceHistory.getLatestByFhirId(params.id)
		readService(h)

	}	

	def vread() {
		ResourceHistory h = ResourceHistory.getFhirVersion(params.id, params.vid)
		readService(h)
	}
	
	def time(label) {
		log.debug("T $label: " + (new Date().getTime() - request.t0))
	}

	def search(PagingCommand paging, SearchCommand query) {

		query.bind(params, request)
		paging.bind(params, request)
		log.debug(query.clauses.toString())
		time("precount $paging.total")

		def cursor = ResourceIndex.collection.find(query.clauses)

		paging.total = cursor.count()
		time("Counted $paging.total tosort ${params.sort}")

		/*
		cursor = cursor.limit(paging._count)
                       .skip(paging._skip)
        */
		
		List<DBObject> summaries = cursor.collect {return it}	

		// TODO: replace this super-slow approach with in-db sorting.
		// Will require reorganizing resourceIndex collection into 
		// separately indexed per-resource collections...
		time("got indexes")
		if(params.sort) {
			summaries.sort {a, b ->
				List avals = a.searchTerms.findAll {it.k == params.sort}
				List bvals = b.searchTerms.findAll {it.k == params.sort}
				if (avals.size() == 0) return 1;
				if (bvals.size() == 0) return -1;
				return (avals[0].v <=> bvals[0].v)
			}
		}
		time("sorted")
		
		if (paging._skip) {
			if (summaries.size() < paging._skip) summaries = []
			summaries = summaries[paging._skip..summaries.size()-1]
		}
		if (summaries.size() > paging._count) {
			summaries = summaries[0..paging._count-1]
		}

		def entriesForFeed = ResourceHistory.getEntriesById(summaries.collect {
			it.latest
		})

		time("Fetched content")
		
		AtomFeed feed = bundleService.atomFeed([
			entries: entriesForFeed,
			paging: paging,
			feedId: fullRequestURI
		])

		time("Made feed")
		request.resourceToRender = feed
	}

}
