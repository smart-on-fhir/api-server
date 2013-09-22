package fhir;

import groovyx.net.http.URIBuilder

import java.util.regex.Pattern

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Resource

class BundleValidationException extends Exception{ }

class BundleService{

	def transactional = false
	def searchIndexService
	def urlService

	void validateFeed(AtomFeed feed) {
		if (feed == null) {
			throw new BundleValidationException('Could not parse a bundle. Ensure you have set an appropriate content-type.')
		}

		if (feed.entryList == null || feed.entryList.size() == 0) {
			throw new BundleValidationException('Did not find any resources in the posted bundle.')
			return
		}
	}
	
	String getResourceName(Resource r) {
		r.class.toString().split('\\.')[-1].toLowerCase()
	}

	AtomFeed atomFeed(p) {
		def feedId = p.feedId
		def entries = p.entries
		def paging = p.paging
	
		AtomFeed feed = new AtomFeed()	
		feed.authorName = "groovy.config.atom.author-name"
		feed.authorUri  = "groovy.config.atom.author-uri"
		feed.id = feedId
		feed.totalResults = paging.total

		if (paging._skip + paging._count < paging.total) {
			def nextPageUrl = nextPageFor(feed.id, paging)
			feed.links.put("next", nextPageUrl)
		}
		
		Calendar now = Calendar.instance
		feed.updated = now
		feed.entryList.addAll entries.collect { id, resource ->
			AtomEntry entry = new AtomEntry()
			entry.id = urlService.fhirBase + '/'+ id
			entry.updated = now
			entry.title = id
			if (resource == null) {
				entry.deleted = true
			} else {
				entry.resource = resource
			}
			return entry
		}

		feed
	}
	
	String nextPageFor(String url, PagingCommand paging) {
		URIBuilder u = new URIBuilder(url)

		if ('_count' in u.query) {
			u.removeQueryParam("_count")
		}
		u.addQueryParam("_count", paging._count)

		if ('_skip' in u.query) {
			u.removeQueryParam("_skip")
		}
		u.addQueryParam("_skip", paging._skip + paging._count)

		return u.toString()
	}

	AtomFeed assignURIs(AtomFeed f) {

		Map assignments = [:]

		// Determine any entry IDs that
		// need reassignment.  That means:
		// 1. IDs that are URNs but not URLs (assign a new ID)
		// 2. IDs that are absolute links to resources on this server (convert to relative)
		// For IDs that already exist in our system, rewrite them to ensure they always
		// appear as relative URIs (relative to [service-base]

		f.entryList.each { AtomEntry e -> 
			boolean needsAssignment = false
			Class c = e.resource.class
			try {
				def asFhirCombinedId = urlService.fhirCombinedId(new URL(urlService.fhirBaseAbsolute, e.id).path)
				if(asFhirCombinedId) {
					assignments[e.id] = asFhirCombinedId
				} else {
					log.debug("no match; ${e.id} needs reassignemnt")
					needsAssignment = true
				}
			} catch(Exception ex) {
				needsAssignment = true
			} finally {
				if (needsAssignment) {
					String r = c.toString().split('\\.')[-1].toLowerCase()
					String id = new ObjectId().toString()
					assignments[e.id] = urlService.relativeResourceLink(r, id)
				}
			}
		}

		def xml = f.encodeAsFhirXml()
		assignments.each {from, to ->
			xml = xml.replaceAll(Pattern.quote(from), to)
			log.debug("Replacing: $from -> $to")
		}

		return xml.decodeFhirXml()
	}
}
