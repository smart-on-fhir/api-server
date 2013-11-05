package fhir.searchParam

import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
import fhir.ResourceIndexReference
import fhir.ResourceIndexTerm
public class ReferenceSearchParamHandler extends SearchParamHandler {

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
					resource_is_external: true,
					resource_id: parts.id,
					resource_type: parts.type,
					resource_version: parts.version
				]))
			}

		}
	}

	@Override
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, fhirId, fhirType) {
		def ret = new ResourceIndexReference()
		ret.search_param = indexedValue.handler.fieldName
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
			return [(fieldName):searchedFor.value]
		}

		if (searchedFor.modifier == "any"){
			return [(fieldName):[$regex: '/'+searchedFor.value+'$']]
		}

		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

}
