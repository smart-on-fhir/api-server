package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import fhir.ResourceIndexString
import fhir.ResourceIndexTerm


public class UriSearchParamHandler extends SearchParamHandler {

  String orderByColumn = "string_value"

  @Override
  protected String paramXpath() {
    return "//$xpath//@value";
  }

  @Override
  public void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r, List<IndexedValue> index) {
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

  def joinOn(SearchedValue v) {
    v.values.split(",").collect {
      List fields = []
      fields += [ name: 'string_value', value: it, operation: 'ILIKE']
      return fields
    }
  }
}
