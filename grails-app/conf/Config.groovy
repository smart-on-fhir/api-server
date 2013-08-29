// locations to search for config files that get merged into the main config;
// config files can be ConfigSlurper scripts, Java properties files, or classes
// in the classpath in ConfigSlurper format

// grails.config.locations = [ "classpath:${appName}-config.properties",
//                             "classpath:${appName}-config.groovy",
//                             "file:${userHome}/.grails/${appName}-config.properties",
//                             "file:${userHome}/.grails/${appName}-config.groovy"]

// if (System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }


fhir.namespaces = [
	f: "http://hl7.org/fhir",
	xhtml: "http://www.w3.org/1999/xhtml"
]

fhir.searchParam.spotFixes = [
"http://hl7.org/fhir/Patient/search#gender": 
	"f:Patient/f:gender",
"http://hl7.org/fhir/Patient/search#given": 
	"f:Patient/f:name/f:given",
"http://hl7.org/fhir/Patient/search#family": 
	"f:Patient/f:name/f:family",
"http://hl7.org/fhir/Patient/search#name": 
	"f:Patient/f:name/*",
"http://hl7.org/fhir/Patient/search#contact": 
	"f:Patient/f:contact/f:value",
"http://hl7.org/fhir/Patient/search#active": 
	"f:Patient/f:active",
"http://hl7.org/fhir/Patient/search#address": 
	"f:Patient/f:address/*",
"http://hl7.org/fhir/Patient/search#animal-breed": 
	"f:Patient/f:animal/f:breed",
"http://hl7.org/fhir/Patient/search#animal-species": 
	"f:Patient/f:animal/f:species",
"http://hl7.org/fhir/Patient/search#birthdate": 
	"f:Patient/f:birthDate",
"http://hl7.org/fhir/Patient/search#identifier": 
	"f:Patient/f:identifier",
"http://hl7.org/fhir/Patient/search#language": 
	"f:Patient/f:communication",
"http://hl7.org/fhir/Patient/search#provider":	
	"f:Patient/f:provider",
"http://hl7.org/fhir/Condition/search#onset": 
	"f:Condition/f:onsetAge | f:Condition/f:onsetDate",
"http://hl7.org/fhir/Group/search#value": 
	"f:Condition/*[namespace-uri()='http://hl7.org/fhir' and starts-with(local-name(),'value')]",
"http://hl7.org/fhir/Observation/search#value": 
	"f:Observation/f:component/*[namespace-uri()='http://hl7.org/fhir' and starts-with(local-name(),'value')]",
"http://hl7.org/fhir/Observation/search#date": 
	"f:Observation/*[namespace-uri()='http://hl7.org/fhir' and starts-with(local-name(),'applies')]",
"http://hl7.org/fhir/Group/search#type-value":
		'f:Group/f:characteristic$'+
		'f:type$' +
		'*[namespace-uri()="http://hl7.org/fhir" and starts-with(local-name(),"value")]',
"http://hl7.org/fhir/DiagnosticOrder/search#status-date":
		'f:DiagnosticOrder/f:event$'+
		'f:status$' +
		'f:date',
"http://hl7.org/fhir/DiagnosticOrder/search#item-status-date":
		'f:DiagnosticOrder/f:item$'+
		'f:status$' +
		"f:date",
"http://hl7.org/fhir/Observation/search#name-value":
		'f:Observation/f:component$' +
		'f:name$' +
		'*[namespace-uri()="http://hl7.org/fhir" and starts-with(local-name(),"value")]'
]



grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination
grails.mime.file.extensions = false // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = true
grails.mime.types = [
    all:           '*/*',
    atom:          'application/atom+xml',
    css:           'text/css',
    csv:           'text/csv',
    form:          'application/x-www-form-urlencoded',
    html:          ['text/html','application/xhtml+xml'],
    js:            'text/javascript',
    json:          ['application/json', 'text/json'],
    multipartForm: 'multipart/form-data',
    rss:           'application/rss+xml',
    text:          'text/plain',
    xml:           ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']

// The default codec used to encode data with ${}
grails.views.default.codec = "none" // none, html, base64
grails.views.gsp.encoding = "UTF-8"
grails.converters.encoding = "UTF-8"
// enable Sitemesh preprocessing of GSP pages
grails.views.gsp.sitemesh.preprocess = true
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.app.context = "/"

environments {
    development {
        grails.logging.jul.usebridge = true		
	fhir.oauth = [
		enabled: true,
		tokenCacheSpec: 'maximumSize=1000,expireAfterWrite=30m',
		introspectionUri: 'http://localhost:8080/openid-connect-server/introspect?token={token}',
		clientId: 'client',
		clientSecret: 'secret'
	]
    }
    production {
        grails.logging.jul.usebridge = false
        grails.serverURL =  System.env.BASE_URL ?: "http://localhost:8080/fhir-server"
	fhir.oauth = [
		enabled: System.env.AUTH ? System.env.AUTH.toBoolean() : true,
		tokenCacheSpec: 'maximumSize=1000,expireAfterWrite=30m',
		introspectionUri: 'http://localhost:8080/openid-connect-server/introspect?token={token}',
		clientId: System.env.CLIENT_ID ?: 'client',
		clientSecret: System.env.CLIENT_SECRET ?: 'secret'
	]
    }
}

// log4j configuration
log4j = {
    // Example of changing the log pattern for the default console appender:
    //
    
	appenders {
        console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    }
	
	debug "grails.app"
    error  'org.codehaus.groovy.grails.web.servlet',        // controllers
           'org.codehaus.groovy.grails.web.pages',          // GSP
           'org.codehaus.groovy.grails.web.sitemesh',       // layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping',        // URL mapping
           'org.codehaus.groovy.grails.commons',            // core / classloading
           'org.codehaus.groovy.grails.plugins',            // plugins
           'org.springframework'
}

// For an example of dynamically loading server URLs from environment vars:
// https://gist.github.com/djensen47/4062384
