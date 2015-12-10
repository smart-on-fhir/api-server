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

cors.headers = [
    'Access-Control-Allow-Headers': 'origin, authorization, accept, content-type, x-requested-with, prefer'
    ]
cors.expose.headers = 'Content-Location,Location'
cors.enabled = true

fhir.namespaces = [
	f: "http://hl7.org/fhir",
	xhtml: "http://www.w3.org/1999/xhtml"
]

fhir.searchParam.spotFixes = [
	"Patient.deceased": "f:Patient/f:deceasedBoolean",
	"Patient.deathdate": "f:Patient/f:deceasedDateTime",
	"Condition.onset":
	"f:Condition/f:onsetAge | f:Condition/f:onsetDate",
	"Group.value":
	"f:Condition/*[namespace-uri()='http://hl7.org/fhir' and starts-with(local-name(),'value')]",
	"Observation.name": 
       "f:Observation/f:name | f:Observation/f:component/f:name",
	"Observation.value-date":
	"f:Observation/f:valuePeriod",
	"Observation.value-quantity":
	"f:Observation/f:valueQuantity",
	"Observation.value-string":
	"f:Observation/f:valueString",
	"Observation.value-concept":
	"f:Observation/f:valueCodeableConcept",
	"Observation.date":
	"f:Observation/*[namespace-uri()='http://hl7.org/fhir' and starts-with(local-name(),'applies')]",
	"Group.type-value":
	'f:Group/f:characteristic$'+
	'f:type$' +
	'*[namespace-uri()="http://hl7.org/fhir" and starts-with(local-name(),"value")]',
	"DiagnosticOrder.status-date":
	'f:DiagnosticOrder/f:event$'+
	'f:status$' +
	'f:date',
	"DiagnosticOrder.item-status-date":
	'f:DiagnosticOrder/f:item$'+
	'f:status$' +
	"f:date",
	"Observation.name-value":
	'f:Observation/f:component | f:Observation$' +
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
	xml:           ['text/xml', 'application/xml', 'application/fhir+xml']
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
grails.converters.json.pretty.print = true;

environments {
	development {
		grails.logging.jul.usebridge = true
		grails.serverURL =  System.env.BASE_URL ?: "http://localhost:9080"
		fhir.oauth = [
			enabled: System.env.AUTH ? System.env.AUTH.toBoolean() : false,
			tokenCacheSpec: System.env.TOKEN_CACHE_SPEC ?: 'maximumSize=1000,expireAfterWrite=30m',
			introspectionUri: System.env.INTROSPECTION_URI ?: 'http://localhost:9085/openid-connect-server-webapp/introspect?token={token}',
			clientId: System.env.AUTH_CLIENT_ID ?: 'client',
			clientSecret: System.env.AUTH_CLIENT_SECRET ?: 'secret',
			registerUri: System.env.REGISTER_URI ?: 'http://localhost:9085/openid-connect-server-webapp/register',
			authorizeUri: System.env.AUTHORIZE_URI ?: 'http://localhost:9085/openid-connect-server-webapp/authorize',
			tokenUri: System.env.TOKEN_URI ?: 'http://localhost:9085/openid-connect-server-webapp/token'
		]
		localAuth = [
			clientId: System.env.CLIENT_ID ?: 'client',
			clientSecret: System.env.CLIENT_SECRET ?: 'secret'
		]
	}
	production {
		grails.logging.jul.usebridge = false
		grails.serverURL =  System.env.BASE_URL ?: "http://localhost:8080/fhir-server"
		fhir.oauth = [
			enabled: System.env.AUTH ? System.env.AUTH.toBoolean() : true,
			tokenCacheSpec: System.env.TOKEN_CACHE_SPEC ?: 'maximumSize=1000,expireAfterWrite=30m',
			introspectionUri: System.env.INTROSPECTION_URI ?: 'http://localhost:8080/openid-connect-server-webapp/introspect?token={token}',
			clientId: System.env.AUTH_CLIENT_ID ?: 'client',
			clientSecret: System.env.AUTH_CLIENT_SECRET ?: 'secret',
			registerUri: System.env.REGISTER_URI ?: 'http://localhost:8080/openid-connect-server-webapp/register',
			authorizeUri: System.env.AUTHORIZE_URI ?: 'http://localhost:8080/openid-connect-server-webapp/authorize',
			tokenUri: System.env.TOKEN_URI ?: 'http://localhost:8080/openid-connect-server-webapp/token'
		]
		localAuth = [
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

// Uncomment and edit the following lines to start using Grails encoding & escaping improvements

/* remove this line 
 // GSP settings
 grails {
 views {
 gsp {
 encoding = 'UTF-8'
 htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
 codecs {
 expression = 'html' // escapes values inside null
 scriptlet = 'none' // escapes output from scriptlets in GSPs
 taglib = 'none' // escapes output from taglibs
 staticparts = 'none' // escapes output from static template parts
 }
 }
 // escapes all not-encoded output at final stage of outputting
 filteringCodecForContentType {
 //'text/html' = 'html'
 }
 }
 }
 remove this line */
