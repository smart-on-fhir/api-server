package fhir;

import grails.plugins.rest.client.RestBuilder

import javax.annotation.PostConstruct

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
	
	def lookup(String toCheck){
		String url = oauth.introspectionUri.replace("{token}", toCheck)
		log.debug("GET " + url)
		def response = rest.get(url) {
			auth(oauth.clientId, oauth.clientSecret)
		}
		response
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
		response.properties.each{
			log.debug(it)
			
		}
	}

}