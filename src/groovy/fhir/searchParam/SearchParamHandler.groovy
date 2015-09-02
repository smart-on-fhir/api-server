package fhir.searchParam

import fhir.ResourceIndexTerm
import fhir.UrlService
import groovy.util.logging.Log4j

import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants

import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance;
import org.w3c.dom.Node


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
  static XPath xpathEvaluator;
  static UrlService urlService;

  String searchParamName;
  def /*SearchParamType */ fieldType;
  String xpath;
  String orderByColumn;
  String resourceName;
  List<String> referenceTypes;

  public static SearchParamHandler create(String searchParamName,
      /*SearchParamType */ fieldType,
      String resourceName,
      String xpath,
      List<String> referenceTypes) {

    String ft = fieldType.toString().toLowerCase().capitalize();
    String className = SearchParamHandler.class.canonicalName.replace(
        "SearchParamHandler", ft + "SearchParamHandler")

    Class c = Class.forName(className,
        true,
        Thread.currentThread().contextClassLoader);

    SearchParamHandler ret =  c.newInstance(
        searchParamName: searchParamName,
        fieldType: fieldType,
        xpath: xpath,
        resourceName: resourceName,
        referenceTypes: referenceTypes
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

  protected void init(){ }

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


  def joinOn(SearchedValue v) {
    throw new Exception("joinOn must be implemented in subclasses");
  }


}
