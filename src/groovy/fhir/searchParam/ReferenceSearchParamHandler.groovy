package fhir.searchParam

import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
public class ReferenceSearchParamHandler extends SearchParamHandler {

	@Override
	protected void processMatchingXpaths(List<Node> nodes, List<SearchParamValue> index) {
		nodes.each {
			index.add(value(queryString('./f:reference/@value', it)));
		}
	}

	@Override
	protected String paramXpath() {
		return "//"+this.xpath;
	}

	@Override
	BasicDBObject searchClause(Map searchedFor){
		// FHIR spec describes a slight difference between
		// no modifier and ":text" on a code --
		// but we're treating them the same here
		if (searchedFor.modifier == null){
			return [(fieldName):searchedFor.value]
		}

		if (searchedFor.modifier == "any"){
			return [(fieldName):[$regex: '/@'+searchedFor.value+'$']]
		}

		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

}
