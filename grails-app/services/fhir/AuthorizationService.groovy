package fhir;

import grails.plugins.rest.client.RestBuilder

import javax.annotation.PostConstruct
import org.codehaus.groovy.grails.exceptions.GrailsException

/**
 * @author jmandel
 *
 */
class AuthorizationService{

	def transactional = false
	def tokenCache
	def grailsApplication
	def rest = new RestBuilder(connectTimeout:2000, readTimeout:2000)
	String authHeader
	Map oauth

	@PostConstruct
	void init() {
		oauth = grailsApplication.config.fhir.oauth
	}

	/**
	 * @param toCheck
	 * @return JSON structure with token introspection details, like
	 {
	 "active": true,
	 "scope": "summary:",
	 "exp": "2013-08-23T20:16:07-0400",
	 "sub": "admin",
	 "client_id": "3ed9d143-c7f6-40e5-b36f-4ad5665c31de",
	 "token_type": "Bearer"
	 }
	 */
	def lookup(String toCheck){
		String url = oauth.introspectionUri.replace("{token}", toCheck)
		log.debug("GET " + url)
		def response = rest.get(url) {
			auth(oauth.clientId, oauth.clientSecret)
		}
		if (response.status != 200)
			return null

		return response.json
	}

	String tokenFor(request){
		def header = request.getHeader("Authorization") =~ /^Bearer (.*)$/
		if(header){
			return header[0][1]
		}
		return false
	}

	boolean adminPassFor(request){
		def header = request.getHeader("Authorization") =~ /^Basic (.*)$/
		log.debug("exampine an admin access password" + header)
		if(header){
			String[] decoded = new String(header[0][1].decodeBase64()).split(':')
			log.debug("try an admin access password" + decoded)
			return (decoded[0] == oauth.clientId && decoded[1] == oauth.clientSecret)
		}
		return false
	}

	def compartmentsFor(request){

		if (request.authorization){

			if ("fhir-admin" in request.authorization.scope)
				return ["patient/@example"]

			def ret = request.authorization.scope.collect {
				def m = (it =~ /(summary|search):(.*)/)
				if (!m.matches()) return null
				return "patient/@" +  (m[0][2] != "" ? m[0][2] : "example")
			}//.findAll {it != null}
			return ret
		}
		// TODO decide on appropriate behavior if authorization is disabled	
		throw new RuntimeException("Can't determine compartments without auth enabled.")
	}

	def compartmentsAllowed(Collection compartments, request){

		if ("fhir-admin" in request.authorization.scope)
			return true

		List<String> expected = compartmentsFor(request)
		return (compartments.every{it in expected})
	}

	def decide(request){
		boolean isAdmin = adminPassFor(request)
		def response

		if (isAdmin) {
			log.debug("Got an admin access password")
			response =  [active: true, scope: "fhir-admin", token_type: "oob"]
		} else {
			def token = tokenFor(request)
			log.debug("Issue a cache req for |$token|" )
			response = tokenCache.cache.get(token, {
				lookup(token)
			})
			log.debug("Found a token for blag: " + response)
		}
		log.debug("Response: " + response.scope.class)
		if (response.scope instanceof String)
			response.scope = [response.scope]

		return response
	}
}
