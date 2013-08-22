package fhir
import com.mongodb.BasicDBList
import com.mongodb.DBObject
import org.bson.types.ObjectId

class ResourceIndex {
	ObjectId id
	ObjectId fhirId
	String type
	Collection searchTerms
}

