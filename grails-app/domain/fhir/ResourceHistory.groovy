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
	Collection compartments
	Date received = new Date()

	static mapping = {
		sort received:'desc'
	}

	static Map getEntriesById(Collection ids) {

		ResourceHistory.collection
				.find( _id: [$in: ids])
				.collectEntries {
					[(it.type.toLowerCase() + '/@' +it.fhirId):
						it.content.toString().decodeFhirJson()]
				}
	}

	static ResourceHistory getLatestByFhirId(String id){

		def h= ResourceHistory.findAllByFhirId(id, [limit:1]).asList()
		if (h.size()==0){
			return null
		}
		h[0]
		//h[0].content.toString().decodeFhirJson()
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

