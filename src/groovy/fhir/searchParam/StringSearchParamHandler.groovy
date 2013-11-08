package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
import fhir.ResourceIndexString
import fhir.ResourceIndexTerm


public class StringSearchParamHandler extends SearchParamHandler {

	String orderByColumn = "string_value"

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
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
		def ret = new ResourceIndexString()
		ret.search_param = indexedValue.handler.searchParamName
		ret.version_id = versionId
		ret.fhir_id = fhirId
		ret.fhir_type = fhirType
		ret.string_value = indexedValue.dbFields.string
		return ret
	}

	@Override
	BasicDBObject searchClause(Map searchedFor){
		
		def val = searchedFor.value
		
		if (searchedFor.modifier == null ||searchedFor.modifier == "partial"){
				return [(searchParamName): [ $regex: val, $options: 'i' ]]
		}
		
		if (searchedFor.modifier == "exact"){
				return [(searchParamName): [ $regex:'^'+val+'$', $options: 'i' ]]
		}
		
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

	def joinOn(SearchedValue v) {
		List ret = ["resource_index_string"]
		List fields = []
		if (v.values){
			fields += [ name: 'string_value', value: v.values+'%', operation: 'LIKE']
		}
		if (v.modifier) {
			fields += [
				name: 'reference_type',
				value: v.modifier	
			]
		}
		return ret + [fields]
	}
}