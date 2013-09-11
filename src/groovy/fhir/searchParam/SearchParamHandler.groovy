package fhir.searchParam

import groovy.util.logging.Log4j

import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants

import org.hl7.fhir.instance.formats.XmlComposer
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import com.mongodb.BasicDBObject
import com.mongodb.DBObject

// TODO implement generic logic for extracting References
public class ReferenceSearchParamHandler extends SearchParamHandler {

	@Override
	protected void processMatchingXpaths(List<Node> nodes, List<SearchParamValue> index) {
		nodes.each {
			index.add(value(queryString('./f:reference/@value', it)));
		}
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

// TODO extract Integer into its own class. Need clarification on
// how this is different from other numerical types (double, say).
public class IntegerSearchParamHandler extends StringSearchParamHandler {

}


/**
 * @author jmandel
 *
 * Instances of this class generate database-ready index terms for a given
 * FHIR resource, based on the declared "searchParam" support in our
 * server-wide conformance profile.
 */
@Log4j
public abstract class SearchParamHandler {

	static XmlParser parser = new XmlParser()
	static XmlComposer composer = new XmlComposer()
	static XPath xpathEvaluator;


	String fieldName;
	SearchParamType fieldType;
	String xpath;

	public static SearchParamHandler create(String fieldName,
			SearchParamType fieldType,
			String xpath) {

		String ft = fieldType.toString().capitalize();
		String className = SearchParamHandler.class.canonicalName.replace(
				"SearchParamHandler", ft + "SearchParamHandler")

		Class c = Class.forName(className,
				true,
				Thread.currentThread().contextClassLoader);


		SearchParamHandler ret =  c.newInstance(
				fieldName: fieldName,
				fieldType: fieldType,
				xpath: xpath
				);
		ret.init();
		return ret;
	}

	static void injectGrailsApplication(injectedXpathEvaluator){
		xpathEvaluator = injectedXpathEvaluator;
	}

	protected void init(){}

	protected abstract void processMatchingXpaths(List<Node> nodes, List<SearchParamValue> index);

	protected abstract String paramXpath()

	List<Node> selectNodes(String path, Node node) {

		// collect to take NodeList --> List<Node>
		xpathEvaluator.evaluate(path, node, XPathConstants.NODESET).collect { it }
	}

	List<Node> query(String xpath, Node n){
		selectNodes(xpath, n)
	}

	public SearchParamValue value(String modifier, Object v){
		new SearchParamValue(
				paramName: fieldName + (modifier ?:""),
				paramType: fieldType,
				paramValue: v
				);
	}

	public SearchParamValue value(Object v){
		value(null,v);
	}

	public String queryString(String xpath, Node n){
		query(xpath, n).collect { it.nodeValue }.join " "
	}

	public List<SearchParamValue> execute(org.w3c.dom.Document r) throws Exception {
		List<SearchParamValue> index = []
		List<Node> nodes = query(paramXpath(), r)
		processMatchingXpaths(nodes, index);
		return index;
	}

	String stripQuotes(Map searchedFor){
		def val = searchedFor.value =~ /^"(.*)"$/
		if (!val.matches()){
			throw new RuntimeException("search strings must be in double quotes: " + searchedFor)
		}
		val[0][1]
	}

	/**
	 * @param param is a single search param (map with keys: key, modifier, value)
	 * @return a list of derived search params, in vase the value has a comma 
	 * 		   or other notion of disjunction built-in.
	 */
	protected List<Map> orClausesFor(Map param){
		println("oring: " + param + param.value + param.class)
		List<String> alternatives = param.value.split(',')
		return alternatives.collect {
			[
				key: param.key,
				modifier: param.modifier,
				value: it
			]
		}
	}


	static BasicDBObject andList(DBObject... clauses){
		andList(clauses)
	}

	static BasicDBObject orList(DBObject... clauses){
		orList(clauses)
	}

	static BasicDBObject orList(Collection<DBObject> clauses){
		onList(clauses, '$or')
	}

	static BasicDBObject andList (Collection<DBObject> clauses){
		onList(clauses, '$and')
	}
	
	static private BasicDBObject onList(Collection<DBObject> clauses, String operation) {
		def nonempty = clauses.findAll {it && it.size() > 0}
		if (nonempty.size() == 0) return [:]
		if (nonempty.size() == 1) return nonempty
		return [(operation): nonempty]		
	}

	abstract BasicDBObject searchClause(Map searchedFor)
}