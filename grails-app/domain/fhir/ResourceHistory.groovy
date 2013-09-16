package fhir

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.DiagnosticOrder;
import org.hl7.fhir.instance.model.Resource

import com.mongodb.DBObject

class ResourceHistory {
	ObjectId id
	String fhirId
	String action
	String type
	DBObject content
	List compartments = []
	Date received = new Date()

	static embedded  = ['compartments']
	static constraints = {
		content nullable: true
	}

	static mapping = {
		sort received:'desc'
	}

	static List getEntriesById(List ids) {
		Map byId = ResourceHistory.collection.find( _id: [$in: ids]).collectEntries {
			[(it._id): it]
		}
		List inOrder = ids.collect {byId[id]}
		return zipIdsWithEntries(inOrder)
	}

	static List zipIdsWithEntries(Iterable entries){
		entries.collect {[
				it.type.toLowerCase() + '/@' +it.fhirId,
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

		if (!ObjectId.isValid(vid)) return null


		List<Resource> vs = ResourceHistory.collection.find([
			'_id':new ObjectId(vid),
			'fhirId':id
		]).limit(1).toList()

		if (vs.size()==0){
			return null
		}
		vs[0]

		//vs[0].content.toString().decodeFhirJson()
	}


}

