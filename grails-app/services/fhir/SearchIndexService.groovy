package fhir;

import javax.annotation.PostConstruct

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hl7.fhir.instance.formats.XmlComposer
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.Conformance
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceComponent
import org.hl7.fhir.instance.model.Conformance.ConformanceRestResourceSearchParamComponent

import com.google.common.collect.ImmutableMap
import com.mongodb.BasicDBObject

import fhir.searchParam.SearchParamHandler
import fhir.searchParam.SearchParamValue

class SearchIndexService{

	static def transactional = false
	static def lazyInit = false
	
	static XmlParser parser = new XmlParser()
	static XmlComposer composer = new XmlComposer()
	static GrailsApplication grailsApplication

	static Map<Class<Resource>,Collection> indexersByResource = [:]
	static Map<String, String> xpathsMissingFromFhir;
	static Map<String, String> capitalizedModelName = [:]

	@PostConstruct
	void init() {
		
		SearchParamHandler.injectGrailsApplication(grailsApplication);

		def xpathFixes = ImmutableMap.<String, String> builder();
		grailsApplication.config.fhir.searchParam.spotFixes.each {  
			uri, xpath -> xpathFixes.put(uri, xpath)
		}
		xpathsMissingFromFhir = xpathFixes.build()
		
		def conformance = resourceFromFile "profile.xml"
		setConformance(conformance)
	}

	public Class<Resource> classForModel(String modelName){
		modelName = capitalizedModelName[modelName]?:modelName
		if(modelName.equals("String")){
			modelName += "_";
		}
		if(modelName.equals("List")){
			modelName += "_";
		}
		log.debug("L:ooking up clasS: " + modelName)
		return lookupClass("org.hl7.fhir.instance.model."+modelName);
	}
	
	public static Resource resourceFromFile(String file) {
		def stream = classLoader.getResourceAsStream(file)
		parser.parse(stream)
	}
	
	public static Class lookupClass(String name){
		Class.forName(name,	true, classLoader)
	}
	
	public static ClassLoader getClassLoader(){
		Thread.currentThread().contextClassLoader
	}

	 
	public void setConformance(Conformance c) throws Exception {
		log.debug("Resetting CONFORMANCE!")
		def restResources = c.rest[0].resource
		restResources.each { resource ->
			capitalizedModelName[resource.typeSimple.toLowerCase()] = resource.typeSimple
			Class model = classForModel resource.typeSimple
			
			indexersByResource[model] = resource.searchParam.collect {	searchParam ->

				String key = searchParam.sourceSimple

				// Short-circuit FHIR's built-in xpath if defined. Handles:
				//  * missing xpaths -- like in Patient
				//  * broken xpaths  -- like 'f:value[x]'
				SearchParamHandler.create(
						searchParam.nameSimple,
						searchParam.typeSimple,
						xpathsMissingFromFhir[key] ?:searchParam.xpathSimple);
			}
		}
	}
	
	public List<SearchParamValue> indexResource(Resource rx) {

		log.info("\n\nINDEXING" + rx)

		def ret = indexersByResource[rx.class].collectMany {
			SearchParamHandler h -> h.execute(rx)
		}

		log.info "\n" + ret.collect {
			"indexed: "+it.paramName+"="+it.paramValue
		}.join("\n")

		return ret;
	}
	
	
	public BasicDBObject queryParamsToMongo(Map params){
		
		def rc = classForModel(params.resource)
		def indexers = indexersByResource[rc]
		
		// Just the indexers for the current resource type
		// keyed on the searchParam name (e.g. "date", "subject")
		Map<String,SearchParamHandler> indexerFor = indexers.collectEntries {
			[(it.fieldName): it]
		}
				
		// Represent each term in the query a
		// key, modifier, and value
		// e.g. [key: "date", modifier: "after", value: "2010"]
		def searchParams = params.collect { k,v ->
			def c = k.split(":") as List
			return [key: c[0], modifier: c[1], value: v]
		  }.findAll {
			   it.key in indexerFor
		  }
		  
		// Run the assigned indexer on each term
		// to generate an AND'able list of MongoDB
		// query clauses.
		List<BasicDBObject> clauses = searchParams.collect {
			
			def idx = indexerFor[it.key]
			List orClauses = idx.orClausesFor(it).collect {
				idx.searchClause(it)
			}
			
			orClauses.size() == 1 ? orClauses[0] : 
									SearchParamHandler.orList(orClauses)
		}
		return SearchParamHandler.andList(clauses)
	}
}