package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject


// FHIR ballot spec doens't fully explain how Text is different from Search...
// for now we'll treat them the same.
public class TextSearchParamHandler extends StringSearchParamHandler {
	
	
}

public class StringSearchParamHandler extends SearchParamHandler {

	@Override
	protected String paramXpath() {
		return "//"+this.xpath+"//@value";
	}

	@Override
	public void processXpathNodes(
			java.util.List<Node> nodes,
			java.util.List<SearchParamValue> index) {

		setMissing(nodes.size() == 0, index);

		Collection<String> parts = new ArrayList<String>();
		for (Node n : nodes) {
			parts.add(n.getNodeValue());
		}
		index.add(value(parts.join(" ")))
	}

	@Override
	BasicDBObject searchClause(def searchedFor){
		
		def val = stripQuotes(searchedFor)
		
		if (searchedFor.modifier == null){
			return match(
				k: fieldName,
				v: [
					$regex: '^'+val,
					$options: 'i'
				]
			)
		}
		
		if (searchedFor.modifier == "exact"){
			return match(
				k: fieldName,
				v: [
					$regex: '^'+val+'$'
				]
			)
		}
		
		if (searchedFor.modifier == "partial"){
			return match(
				k: fieldName,
				v: [
					$regex: val+'$',
					$options: 'i'
				]
			)
		}
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}
}
