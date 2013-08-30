package fhir
import com.mongodb.BasicDBList
import com.mongodb.DBObject
import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed

class ResourceIndex {
	ObjectId id
	String fhirId
	ObjectId latest
	String type
	Collection searchTerms
	Collection compartments
	def grailsLinkGenerator	

	static Map entriesForFeed(cursor) {
		List ids = cursor.collect {
			it.latest
		}

		ResourceHistory.collection
				.find( _id: [$in: ids])
				.collectEntries {
					[(it.type.toLowerCase() + '/' +it.fhirId):
						it.content.toString().decodeFhirJson()]
				}
	}

	static AtomFeed atomFeed(p) {
		def feedId = p.feedId
		def entries = p.entries
		def paging = p.paging
	
		String base = grailsLinkGenerator.link(uri:'', absolute:true) + '/fhir/'
		AtomFeed feed = new AtomFeed()	
		feed.authorName = "groovy.config.atom.author-name"
		
		feed.authorUri  = "groovy.config.atom.author-uri"
		feed.id = feedId
		feed.totalResults = entries.size()
		if (paging._skip + paging._count < paging.total) {
			def nextPage = paging._skip + paging._count
			feed.links.put("next", feed.id + "?_count="+paging._count+"&_skip=$nextPage")
		}
		
		Calendar now = Calendar.instance
		feed.updated = now
			
		
		feed.entryList.addAll entries.collect { id, resource ->
			AtomEntry entry = new AtomEntry()
			entry.id = base + id
			entry.resource = resource
			entry.updated = now
			return entry
		}

		feed
	}
}
