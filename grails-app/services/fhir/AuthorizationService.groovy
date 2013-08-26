package fhir;

import grails.plugins.rest.client.RestBuilder

import javax.annotation.PostConstruct

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
	
	def compartmentFor(request){
		"patient/@123"
	}

	
	def decide(request){
		boolean isAdmin = adminPassFor(request)
		if (isAdmin) {
			log.debug("Got an admin access password")
			return [active: true, scope: "fhir-admin", token_type: "oob"]
		}

		def token = tokenFor(request)
		log.debug("Issue a cache req for |$token|" )
		def response = tokenCache.cache.get(token, {
			def r = lookup(token)
			log.debug("Response from cache: " + r)
			r
		})
		log.debug("Found a token for blag: " + response)
		return response
	}
}
