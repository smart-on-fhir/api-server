package fhir.searchParam

import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
import fhir.ResourceIndexReference
import fhir.ResourceIndexTerm
public class ReferenceSearchParamHandler extends SearchParamHandler {

	String orderByColumn = "reference_id"

	@Override
	protected void processMatchingXpaths(List<Node> nodes, List<IndexedValue> index) {
		


		nodes.each {

			String ref = query('./f:reference/@value', it).collect{it.nodeValue}.join("");
			Map parts = urlService.fhirUrlParts(ref)

			if (!parts['type'])  {
				index.add(value([
					resource_is_external: true,
					resource_id: ref
				]))
			} else {
				index.add(value([
					resource_is_external: false,
					resource_id: parts.id,
					resource_type: parts.type,
					resource_version: parts.version
				]))
			}

		}
	}
	
	def joinOn(SearchedValue v) {
		List ret = ["resource_index_reference"]
		List fields = []
		if (v.values){
			fields += [ name: 'reference_id', value: v.values]
			fields += [ name: 'reference_version', value: "TODO"]
		}
		if (v.modifier) {
			fields += [
				name: 'reference_type',
				value: v.modifier	
			]
		}
		return ret + [fields]
	}


	@Override
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
		def ret = new ResourceIndexReference()
		ret.search_param = indexedValue.handler.searchParamName
		ret.version_id = versionId
		ret.fhir_id = fhirId
		ret.fhir_type = fhirType
		ret.reference_id = indexedValue.dbFields.resource_id
		ret.reference_type = indexedValue.dbFields.resource_type
		ret.reference_version = indexedValue.dbFields.resource_version
		ret.reference_is_external = false // TODO support external refs
		return ret
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
			return [(searchParamName):searchedFor.value]
		}

		if (searchedFor.modifier == "any"){
			return [(searchParamName):[$regex: '/'+searchedFor.value+'$']]
		}

		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

}
