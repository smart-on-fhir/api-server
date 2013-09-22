package fhir.searchParam

import java.util.List;

import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
public class IdSearchParamHandler extends SearchParamHandler {

	@Override
	BasicDBObject searchClause(Map searchedFor){
		// FHIR spec describes a slight difference between
		// no modifier and ":text" on a code --
		// but we're treating them the same here
		if (searchedFor.modifier == null){
			return [fhirId: searchedFor.value]
		}
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}
	
	@Override
	protected String paramXpath() {
		throw new Exception("Should not use Id Search Parameter to index a resource");
	}



	@Override
	protected void processMatchingXpaths(List<Node> nodes,
			List<SearchParamValue> index) {
		throw new Exception("Should not use Id Search Parameter to index a resource");
	}

}
