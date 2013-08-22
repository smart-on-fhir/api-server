package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import com.mongodb.BasicDBObject;

// Per FHIR spec: May be used to search through the
// * text, displayname, code and code/codesystem (for codes)
// * and label, system and key (for identifier)
public class TokenSearchParamHandler extends SearchParamHandler {
	// :text (the match does a partial searches on
	//          * the text portion of a CodeableConcept or
	//            the display portion of a Coding)
	// :code (a match on code and system of
	//          * the coding/codeable concept)
	// :anyns matches all codes irrespective of the namespace.

	@Override
	protected String paramXpath() {
		return "//"+this.xpath;
	}

	public void processXpathNodes(
			java.util.List<Node> tokens,
			java.util.List<SearchParamValue> index) throws Exception {

		if (!fieldName.equals("_id")) {
			setMissing(tokens.size() == 0, index);
		}

		for (Node n : tokens) {

			def List<String> textParts = [];
			// :text (the match does a partial searches on
			//          * the text portion of a CodeableConcept or
			//            the display portion of a Coding)

			query(".//@value", n).each { textPart->
				textParts.add(textPart.getNodeValue());
				if (textPart.getNodeValue() == null){
					//Logger.info("Hmm, null @value for " + fieldName + fieldType + xpath);
				}
			}

			index.add(value(":text", textParts.join(" ")))


			// For CodeableConcept and Coding, list the code as "system/code"
			query(".//f:code", n).each { codePart ->
				String code = queryString("./@value", codePart);
				String system = queryString("../f:system/@value", codePart);

				index.add(value(":code", system+"/"+code));
			}

			// For Identifier, list the code as "system/key"
			for (Node codePart : query(".//f:key", n)) {

				String code = queryString("./@value", codePart);
				String system = queryString("../f:system/@value", codePart);

				index.add(value(":code", system+"/"+code));
			}

			// For plain 'ol Code elements, we'll at least pull out the value
			// (We won't try to determine the implicit system for now, since
			//  it's not available in instance data or profile.xml)
			query("./@value", n).each { codePart->
				index.add(value(":code", codePart.getNodeValue()));
			}

		}
	}
			

	@Override
	BasicDBObject searchClause(def searchedFor){
		
		// FHIR spec describes a slight difference between
		// no modifier and ":text" on a code -- 
		// but we're treating them the same here
		if (searchedFor.modifier in [null, "text"]){
			return match(
					k: fieldName+':text',
					v: searchedFor.value
				)
		}
		
		if (searchedFor.modifier == "code"){
			return match(
				k: fieldName+':code',
				v: searchedFor.value
			)
		}

		if (searchedFor.modifier == "anyns"){
			return match(
				k: fieldName+':code',
				v: [$regex: '/'+searchedFor.value+'$']
			)
		}

		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}

}
