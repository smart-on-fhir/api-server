package fhir.searchParam

import java.util.Map;

import org.hl7.fhir.instance.model.Resource
//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import com.mongodb.BasicDBObject;

import fhir.ResourceIndexComposite;
import fhir.ResourceIndexTerm

// Generates "composite" matches based on FHIR spec.  But unclear whether/how
// composites interact with modifiers.  Are the terms in a composite individually
// modifiable?  It not, how can one express "before this date" in a status-date pair?

public class CompositeSearchParamHandler extends SearchParamHandler {

  String orderByColumn = "composite_value"

  private String parent;
  private List<String> children = []

  @Override
  protected void init(){
    if (xpath == null) {
      println("No composite xpath for " + searchParamName)
      return
    }
    def paths = xpath.split('\\$');
    parent = paths[0];
    children = paths[1..-1].collect { "./$it/@value"; }
  }

  @Override
  protected String paramXpath() {
    "//$parent"
  }

  @Override
  public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
    def ret = new ResourceIndexComposite()
    ret.search_param = indexedValue.handler.searchParamName
    ret.version_id = versionId
    ret.fhir_id = fhirId
    ret.fhir_type = fhirType
    ret.composite_value = indexedValue.dbFields.composite
    return ret
  }

  @Override
  public void processMatchingXpaths(List<Node> compositeRoots, org.w3c.dom.Document r, List<IndexedValue> index){

    for (Node n : compositeRoots) {
      List<String> combined = [];

      for (String child : children) {
        List<Node> childMatches = query(child, n);
        if (childMatches.size() > 1) {
          throw new Exception("Expected <= 1 composite child for " +
          parent + "/" + child);
        } else if (childMatches.size() == 1){
          combined.add(childMatches.get(0).nodeValue);
        }
      }

      if (combined.size() == children.size()) {
        index.add(value([
          composite: combined.join('$')
        ]))
      }

    }
  }

}
