package fhir;

import java.util.regex.Pattern

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed

class BundleValidationException extends Exception{
	
}

class BundleService{

	def transactional = false
	def searchIndexService
	def grailsLinkGenerator	

	private def fhirCombinedId(String p) {
		def ret = p =~ /\/fhir\/([^\/]+)\/@([^\/]+)(?:\/history\/@([^\/]+))?/
		if (ret.matches()) return ret[0][1] + '/@' + ret[0][2]
		return null
	}

	def getBaseURI() {
		grailsLinkGenerator.link(uri:'', absolute:true)
	}

	def getFhirBaseAsURL() {
		new URL(baseURI + '/fhir/')
	}
	
	String relativeResourceLink(String resource, String id) {
		grailsLinkGenerator.link(
			mapping:'resourceInstance',
			params: [resource:resource, id:id
		]).replace("%40","@")[6..-1]
	}
	
	void validateFeed(AtomFeed feed) {

		if (feed == null) {
			throw new BundleValidationException('Could not parse a bundle. Ensure you have set an appropriate content-type.')
		}

		if (feed.entryList == null || feed.entryList.size() == 0) {
			throw new BundleValidationException('Did not find any resources in the posted bundle.')
			return
		}
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
				def asFhirCombinedId = fhirCombinedId(new URL(fhirBaseAsURL, e.id).path)
				if(asFhirCombinedId) {
					assignments[e.id] = asFhirCombinedId
				} else {
					needsAssignment = true
				}
			} catch(Exception ex) {
				needsAssignment = true
			} finally {
				if (needsAssignment) {
					String r = c.toString().split('\\.')[-1].toLowerCase()
					String id = new ObjectId().toString()
					assignments[e.id] = relativeResourceLink(r, id)
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