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
	String fhirType
	Collection searchTerms
	Collection compartments

	static def forResource(String collectionName) {
		ResourceIndex.collection.DB.getCollection(collectionName+'Index')
	}

}
