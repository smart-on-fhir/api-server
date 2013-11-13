package fhir.searchParam

import fhir.ResourceIndexTerm
import fhir.UrlService
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
	static UrlService urlService;

	String searchParamName;
	SearchParamType fieldType;
	String xpath;
	String orderByColumn;
	String resourceName;

	public static SearchParamHandler create(String searchParamName,
			SearchParamType fieldType,
			String resourceName,
			String xpath) {

		String ft = fieldType.toString().capitalize();
		String className = SearchParamHandler.class.canonicalName.replace(
				"SearchParamHandler", ft + "SearchParamHandler")

		Class c = Class.forName(className,
				true,
				Thread.currentThread().contextClassLoader);


		SearchParamHandler ret =  c.newInstance(
				searchParamName: searchParamName,
				fieldType: fieldType,
				xpath: xpath,
				resourceName: resourceName
				);
		ret.init();
		return ret;
	}

	static void injectXpathEvaluator(XPath injectedXpathEvaluator){
		xpathEvaluator = injectedXpathEvaluator;
	}
	static void injectUrlService(UrlService urlServiceIn){
		urlService = urlServiceIn;
	}

	protected void init(){}

	protected abstract void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r, List<IndexedValue> index);

	protected abstract String paramXpath()

	List<Node> selectNodes(String path, Node node) {

		// collect to take NodeList --> List<Node>
		xpathEvaluator.evaluate(path, node, XPathConstants.NODESET).collect { it }
	}

	List<Node> query(String xpath, Node n){
		selectNodes(xpath, n)
	}
	
	
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
		throw new Exception("createIndex not implemented")
	}

	public IndexedValue value(Object v){
		new IndexedValue(
				dbFields: v,
				paramName: searchParamName,
				handler: this
		);
	}

	public String queryString(String xpath, Node n){
		query(xpath, n).collect { it.nodeValue }.join " "
	}

	public List<IndexedValue> execute(org.w3c.dom.Document r) throws Exception {
		List<IndexedValue> index = []
		List<Node> nodes = query(paramXpath(), r)
		processMatchingXpaths(nodes, r, index);
		return index;
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