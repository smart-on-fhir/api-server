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

	static Map getEntriesById(Collection ids) {

		ResourceHistory.collection
				.find( _id: [$in: ids])
				.collectEntries {
					[(it.type.toLowerCase() + '/@' +it.fhirId):
						it.content.toString().decodeFhirJson()]
				}
	}


}
