package fhir

import org.hl7.fhir.instance.model.DiagnosticOrder;
import org.hl7.fhir.instance.model.Resource

import com.mongodb.DBObject

class ResourceHistory {
	String fhirId
	String action
	String fhirType
	List compartments = []
	Date received = new Date()

	static embedded  = ['compartments']
	static constraints = {
	}

	static mapping = {
		sort received:'desc'
	}

	static List getEntriesById(List ids) {
		Map byId = ResourceHistory.collection.find( _id: [$in: ids]).collectEntries {
			[(it._id): it]
		}
		List inOrder = ids.collect {byId[it]}
		println("In order: $inOrder")
		return zipIdsWithEntries(inOrder)
	}

	static List zipIdsWithEntries(Iterable entries){
		entries.findAll { it != null }
		       .collect {[
				it.fhirType.toLowerCase() + '/' +it.fhirId,
				it.content ? it.content.toString().decodeFhirJson() : null
			]}
	}

	static ResourceHistory getLatestByFhirId(String id){
		def h= ResourceHistory.findAllByFhirId(id, [limit:1]).asList()
		if (h.size()==0){
			return null
		}
		h[0]
	}

	static ResourceHistory getFhirVersion(String id, String vid){
		return null
	}


}

