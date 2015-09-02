package fhir.searchParam

//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import fhir.ResourceIndexTerm
import fhir.ResourceIndexToken


/**
 * @author jmandel
 *  Per FHIR spec, token-type search params can search through
 *    - text, displayname, code and code/codesystem (for codes)
 *    - label, system and key (for identifier)
 */
public class TokenSearchParamHandler extends SearchParamHandler {
  /*	 :text (the match does a partial searches on
   *	          - the text portion of a CodeableConcept or
   *	          -  the display portion of a Coding)
   *	 :code (a match on code and system of
   *	          - the coding/codeable concept)
   *	 :anyns matches all codes irrespective of the namespace.
   */

  String orderByColumn = "token_text"

  @Override
  protected String paramXpath() {
    return "//"+this.xpath;
  }


  void processMatchingXpaths(List<Node> tokens, org.w3c.dom.Document r, List<IndexedValue> index){

    for (Node n : tokens) {

      // :text (the match does a partial searches on
      //          * the text portion of a CodeableConcept or
      //            the display portion of a Coding or
      //            the label portion of an Identifier)
      String text = queryString(".//f:label/@value | .//f:display/@value | .//f:text/@value", n)

      // For CodeableConcept and Coding, list the code as "system/code"
      // For Identifier, list the code as "system/value"
      query(".//f:code | .//f:value", n).each { systemPart ->
        String system = queryString("../f:system/@value", systemPart);
        String code = queryString("./@value", systemPart);
        index.add(value([
          namespace: system,
          code: code,
          text: text
        ]))
      }

      // For plain 'ol Code elements, we'll at least pull out the value
      // (We won't try to determine the implicit system for now, since
      //  it's not available in instance data or profile.xml)
      query("./@value", n).each {Node codePart->
        index.add(value([
          code: codePart.nodeValue
        ]))
      }
    }
  }

  @Override
  public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
    def ret = new ResourceIndexToken()
    ret.search_param = indexedValue.handler.searchParamName
    ret.version_id = versionId
    ret.fhir_id = fhirId
    ret.fhir_type = fhirType
    ret.token_code = indexedValue.dbFields.code
    ret.token_namespace  = indexedValue.dbFields.namespace
    ret.token_text = indexedValue.dbFields.text
    return ret
  }

  private List splitToken(String t) {
    List v = t.split("\\|")
    if (v.size() == 1) {
      if (t.startsWith("\\|")) return [null, v[0]]
      return ["anyns", v[0]]
    }
    return [v[0], v[1]]
  }

  @Override
  def joinOn(SearchedValue v) {
    v.values.split(",").collect {
      def (namespace, code) = splitToken(it)
      List fields = []

      if (v.modifier == null){
        if (namespace == null) {
          fields += [ name: 'token_namespace', operation: 'is null' ]
        }
        if (!(namespace in [null, "anyns"])) {
          fields += [ name: 'token_namespace', value: namespace ]
        }
        fields += [ name: 'token_code', value: code ]
      }

      if (v.modifier == "text"){
        fields += [ name: 'token_text', operation:'ILIKE', value: '%'+it+'%' ]
      }

      return fields
    }
  }

}
