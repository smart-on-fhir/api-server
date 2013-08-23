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
		String h = "$oauth.clientId:$oauth.clientSecret"
		authHeader = h.bytes.encodeBase64().toString()
		log.debug("With auth header: " + authHeader)
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

	boolean decide(request){
		def token = tokenFor(request)
		log.debug("Issue a cache req for |$token|" )
		def response = tokenCache.cache.get(token, {
			log.debug("Cache miss; looking up")
			lookup(token)
		})
		log.debug("Found a token for blag: " + response)
	}

}