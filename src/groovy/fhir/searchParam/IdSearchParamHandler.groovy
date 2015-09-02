package fhir.searchParam

import java.util.List;

//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

public class IdSearchParamHandler extends SearchParamHandler {

  @Override
  protected String paramXpath() {
    throw new Exception("Should not use Id Search Parameter to index a resource");
  }

  @Override
  protected void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r,
      List<IndexedValue> index) {
    throw new Exception("Should not use Id Search Parameter to index a resource");
  }

  def joinOn(SearchedValue v) {
    v.values.split(",").collect {
      List fields = []
      fields += [
        name: 'fhir_id',
        value: it
      ]
      return fields
    }
  }

}
