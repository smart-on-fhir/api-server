package fhir

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Patient

import com.mongodb.BasicDBObject
import com.mongodb.DBApiLayer
import com.mongodb.DBObject

import fhir.searchParam.DateSearchParamHandler;
import fhir.searchParam.SearchParamValue


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

	def getClauses() {
		return request.authorization.restrictSearch(clauses)
	}

	def bind(params, request) {

		this.request = request
		this._since = params._since ?  DateSearchParamHandler.precisionInterval(params._since).start.toDate() : null

		if (_since) {
			clauses['received'] = [ $gte: _since ]
		}
		if (params.resource) {
			String resource = searchIndexService.capitalizedModelName[params.resource]
			clauses['type'] = resource
		}
		if (params.id) {
			clauses['fhirId'] =  params.id
		}
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

class ResourceDeletedException extends Exception {
	def ResourceDeletedException(String msg){
		super(msg)
	}
}

class ApiController {

	static scope = "singleton"
	def searchIndexService
	def authorizationService
	BundleService bundleService
	UrlService urlService

	def getFullRequestURI(){
		urlService.fullRequestUrl(request)
	}

	def welcome() {
		render(view:"/index")
	}

	def conformance(){
		request.resourceToRender = searchIndexService.conformance
	}

	// Note that we don't offer real transaction (all-or-none) semantics
	// but we do allow clients to apply a group of changes in a single
	// request.  So it's  more like "batch" than a transaction...
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

		feed.entryList.each { AtomEntry e ->
			String r = e.resource.class.toString().split('\\.')[-1].toLowerCase()
			updateService(e.resource, r, e.id.split('@')[1])
		}

		request.resourceToRender =  feed
	}

	// BlueButton-specific "summary" API: returns the most recent C-CDA
	// clinical summary (resolving DocumentReference -- Document)
	def summary() {
		log.debug("Compartments: " + request.authorization.compartments)

		def q = [
			type:'DocumentReference',
			compartments:[$in:request.authorization.compartments],
			searchTerms: [ $elemMatch: [
					k:'type:code',
					v:'http://loinc.org/34133-9']]]

		List ids = ResourceIndex.forResource('DocumentReference')
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

		def compartments = getAndAuthorizeCompartments(r, fhirId)
		log.debug("Compartments: $compartments")

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
				compartments: compartments as String[],
				type: type,
				action: 'POST',
				content: rjson).save()

		def rIndex = [
			fhirId: fhirId,
			compartments: compartments as String[],
			latest: h.id,
			type: type,
		]

		indexTerms.groupBy { it.paramName }.each {k, vlist ->
			def values = vlist.collect {it.paramValue}
			if (values.size())
				rIndex[k] = values
		}

		log.debug("Writing rindex:  $rIndex")

		def collection = ResourceIndex.forResource(type)
		collection.remove(new BasicDBObject([
			fhirId: fhirId,
			type: type
		]))

		collection.insert(new BasicDBObject(rIndex))

		log.debug("Got $collection to insert $rIndex")
		versionUrl = urlService.resourceVersionLink(resourceName, fhirId, h.id.toString())

		log.debug("Created version: " + versionUrl)
		response.setHeader('Location', versionUrl)
		response.setStatus(201)
		request.resourceToRender = r
	}

	def update() {
		def body = request.reader.text
		Resource r;

		if (params.resource == 'binary')  {
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

	private List<String> getAndAuthorizeCompartments(r, String fhirId) {
		def compartments = params.list('compartments').collect {it}
		log.debug("Authorizing comparemtns start from: $compartments")

		if ("compartments" in r.properties){
			compartments.addAll(r.compartments)
		} else if (r instanceof Patient) {
			compartments.add("patient/@$fhirId")
		} else if ("subject" in r.properties) {
			if (r.subject.typeSimple == 'Patient') {
				compartments.add(r.subject.referenceSimple)
			}
		} else if ("patient" in r.properties) {
			if (r.patient.typeSimple == 'Patient') {
				compartments.add(r.patient.referenceSimple)
			}
		}

		if (!request.authorization.allows(operation: "PUT", compartments: compartments)){
			throw new AuthorizationException("Can't write to compartments: $compartments")
		}
		return compartments
	}

	private def deleteService(ResourceHistory h, String fhirId) {
		List<String> compartments = getAndAuthorizeCompartments(h, fhirId);
		log.debug("Compartments: $compartments")
		String type = h.type


		Map dParams = [
			fhirId: fhirId,
			compartments: compartments as String[],
			type: h.type,
			action: 'DELETE',
			content: null
		]
		log.debug("Deleting $dParams")

		ResourceHistory deleteEntry = new ResourceHistory(dParams).save()

		log.debug("Deleted $deleteEntry")

		ResourceIndex.forResource(type).remove(new BasicDBObject([
			fhirId: fhirId,
			type: type
		]))

		log.debug("Deleted resource: " + fhirId)
		response.setStatus(204)
	}

	def delete() {
		String fhirId = params.id

		// TODO DRY this logic with 'getOr404'... or pre-action
		// functions that handle this logic generically across
		// GET, PUT, DELETE...
		ResourceHistory h = ResourceHistory.getLatestByFhirId(fhirId)
		if (!h){
			response.status = 404
			return
		}
		request.authorization.require(operation:'DELETE', resource:h)
		deleteService(h, fhirId)
	}

	private void readService(ResourceHistory h) {

		if (!h){
			response.status = 404
			return
		}

		if (h.action == 'DELETE'){
			throw new ResourceDeletedException("Resource ${h.fhirId} has been deleted. Try fetching its history.")
		}

		request.authorization.require(operation:'GET', resource:h)
		log.debug("K, ,authorized")
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

		String resource = searchIndexService.capitalizedModelName[params.resource]
		def cursor = ResourceIndex.forResource(resource).find(query.clauses)
		if (params.sort) {
			cursor = cursor.sort(new BasicDBObject([(params.sort):1]))
		}

		paging.total = cursor.count()
		time("Counted $paging.total tosort ${params.sort}")

		cursor = cursor.limit(paging._count)
				.skip(paging._skip)

		def entriesForFeed = ResourceHistory.getEntriesById(cursor.collect {
			it.latest
		})

		time("Fetched content of size ${entriesForFeed.size()}")

		AtomFeed feed = bundleService.atomFeed([
			entries: entriesForFeed,
			paging: paging,
			feedId: fullRequestURI
		])

		time("Made feed")
		request.resourceToRender = feed
	}

	def history(PagingCommand paging, HistoryCommand query) {
		query.bind(params, request)
		paging.bind(params, request)

		log.debug("history query: " + query.clauses.toString())

		def cursor = ResourceHistory.collection
				.find(query.clauses)
				.sort(received: 1)

		paging.total = cursor.count()
		time("Counted $paging.total tosort ${params.sort}")

		cursor = cursor
				.limit(paging._count)
				.skip(paging._skip)


		def entriesForFeed = ResourceHistory.zipIdsWithEntries(cursor)

		time("Fetched content of size ${entriesForFeed.size()}")

		AtomFeed feed = bundleService.atomFeed([
			entries: entriesForFeed,
			paging: paging,
			feedId: fullRequestURI
		])

		time("Made feed")
		request.resourceToRender = feed
	}

}