package fhir.searchParam

//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import fhir.ResourceIndexReference
import fhir.ResourceIndexTerm
public class ReferenceSearchParamHandler extends SearchParamHandler {

  String orderByColumn = "reference_id"

  @Override
  protected void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r, List<IndexedValue> index) {
    nodes.each {
      String ref = query('./f:reference/@value', it).collect{it.nodeValue}.join("");
      Map parts = urlService.fhirUrlParts(ref)

      if (ref.startsWith("#")) {
        index.add(value([
          contained_id: ref[1..-1],
          contained_type: query("//f:contained/*/f:id[@value='"+ref[1..-1]+"']/..", r).collect{it.nodeName}.join("")
        ]))
      }
      else if (!parts['type'])  {
        index.add(value([
          reference_is_external: true,
          reference_id: ref
        ]))
      } else {
        index.add(value([
          raw: ref,
          reference_is_external: false,
          reference_id: parts.id,
          reference_type: parts.type,
          reference_version: parts.version
        ]))
      }
    }
  }

  def joinOn(SearchedValue v) {
    if (v.values == null) return []
    v.values.split(",").collect {
      List fields = []
      if (it){
        fields += [ name: 'reference_id', value: it]
      }
      if (v.modifier) {
        fields += [
          name: 'reference_type',
          value: v.modifier
        ]
      }
      return fields
    }
  }


  @Override
  public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
    def ret = new ResourceIndexReference()
    ret.search_param = indexedValue.handler.searchParamName
    ret.version_id = versionId
    ret.fhir_id = fhirId
    ret.fhir_type = fhirType

    if (indexedValue.dbFields.contained_id) {
      ret.reference_id = fhirId+"_contained_"+indexedValue.dbFields.contained_id
      ret.reference_type = indexedValue.dbFields.contained_type
    } else {
      ret.reference_type = indexedValue.dbFields.reference_type
      ret.reference_id = indexedValue.dbFields.reference_id
    }

    ret.reference_version = indexedValue.dbFields.resource_version

    ret.reference_is_external = false // TODO support external refs
    return ret
  }

  @Override
  protected String paramXpath() {
    return "//"+this.xpath;
  }

}
