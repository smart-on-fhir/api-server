package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
import fhir.ResourceIndexString
import fhir.ResourceIndexTerm


public class StringSearchParamHandler extends SearchParamHandler {

	@Override
	protected String paramXpath() {
		return "//$xpath//@value";
	}

	@Override
	public void processMatchingXpaths(List<Node> nodes, List<IndexedValue> index) {
		String parts = nodes.collect {it.nodeValue}.join(" ")
		index.add(value([
			string: parts	
		]))
	}
	
	@Override
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, fhirId, fhirType) {
		def ret = new ResourceIndexString()
		ret.search_param = indexedValue.handler.fieldName
		ret.fhir_id = fhirId
		ret.fhir_type = fhirType
		ret.string_value = indexedValue.dbFields.string
		return ret
	}

	@Override
	BasicDBObject searchClause(Map searchedFor){
		
		def val = searchedFor.value
		
		if (searchedFor.modifier == null ||searchedFor.modifier == "partial"){
				return [(fieldName): [ $regex: val, $options: 'i' ]]
		}
		
		if (searchedFor.modifier == "exact"){
				return [(fieldName): [ $regex:'^'+val+'$', $options: 'i' ]]
		}
		
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

}