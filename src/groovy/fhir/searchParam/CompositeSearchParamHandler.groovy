package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node
import org.w3c.dom.NodeList

// Generates "composite" matches based on FHIR spec.  But unclear whether/how
// composites interact with modifiers.  Are the terms in a composite individually
// modifiable?  It not, how can one express "before this date" in a status-date pair?

public class CompositeSearchParamHandler extends SearchParamHandler {

	private String parent;
	private List<String> children = []

	@Override
	protected void init(){
		def paths = xpath.split('\\$');
		parent = paths[0];
		for (int i=1; i<paths.length; i++){
			children.add("./"+paths[i]+"/@value");
		}
	}

	@Override
	protected String paramXpath() {
		return "//"+this.parent;
	}

	public void processXpathNodes(
			java.util.List<Node> compositeRoots,
			java.util.List<SearchParamValue> index) throws Exception {


		for (Node n : compositeRoots) {
			List<String> combined = new ArrayList<String>();

			for (String child : children) {
				List<Node> childMatches = query(child, n);

				if (childMatches.size() > 1) {
					throw new Exception("Expected <= 1 composite child for " +
					parent + "/" + child);
				} else if (childMatches.size() == 1){
					combined.add(childMatches.get(0).getNodeValue());
				}
			}

			if (combined.size() == children.size()) {
				index.add(value(combined.join('$')))
			}

		}
		setMissing(index.size() == 0, index);
	}
}