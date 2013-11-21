package fhir;

import com.mongodb.BasicDBObject
import grails.plugins.rest.client.RestBuilder
import fhir.searchParam.SearchParamHandler

import javax.annotation.PostConstruct
class AuthorizationException extends Exception {
	def AuthorizationException(String msg){
		super(msg)
	}
}

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

	Authorization asBasicAuth(request){
		def header = request.getHeader("Authorization") =~ /^Basic (.*)$/
		log.debug("exampine an admin access password" + header)
		if(header){
			String[] decoded = new String(header[0][1].decodeBase64()).split(':')
			log.debug("try an admin access password" + decoded)
			if (decoded[0] == oauth.clientId && decoded[1] == oauth.clientSecret) {
				def ret = new Authorization(isAdmin: true)
				return ret
			}
		}
		return null
	}

	Authorization asBearerAuth(request){
		def header = request.getHeader("Authorization") =~ /^Bearer (.*)$/
		if(header){
			def token = header[0][1]
			log.debug("Issue a cache req for |$token|" )
			def status = tokenCache.cache.get(token, {
				lookup(token)
			})
			log.debug("remapping: $status")

			// Token introspection param names have changed; support old + new
			def mappedStatus = [
				active: status.active ?: status.valid,
				exp: status.exp ?: status.expires_at,
				sub: status.sub ?: status.subject,
				client_id: status.client_id,
				scope: status.scope
			]

			status = mappedStatus
			log.debug("STatus: $status")

			if (!status.active) return null;
			Date exp = org.joda.time.format.ISODateTimeFormat.dateTimeParser()
					.parseDateTime(status.exp).toDate()
			def ret = new Authorization(
					isAdmin: "fhir-complete" in status.scope,
					isActive:status.active,
					expiration: exp,
					username: status.sub,
					app: status.client_id)

			if (status.scope.class == String) status.scope = [status.scope]
			ret.compartments = status.scope.collect {
				def m = (it =~ /(summary|search):(.*)/)
				if (!m.matches()) return null
				return "Patient/" +  (m[0][2] ?: "example")
			}

			log.debug("Found bearer authorization for this request: $ret")
			return ret
		}
		return null
	}


	public class Authorization {
		private boolean isAdmin
		boolean isActive
		Date expiration
		String username
		String app
		List<String> compartments = ["Patient/example"]

		boolean allows(p) {
			// String operation, Class resource, List<String> compartmentsToCheck
			if (isAdmin) return true
			if (!isActive) return false

			p.compartments.every{ String c ->
				c in compartments
			}
		}

		void require(p) {
			if (isAdmin) return

			if (!compartments.any {it in p.resource.compartments})
				throw new AuthorizationException("Unauthorized:  you only have access to " + compartments + "not $p")
		}

        String getCompartmentsSql() {
			return "'{"+compartments.join(",")+"}'"
        }
		
		def restrictSearch(clauses) {
			if (isAdmin) return clauses
			def extra = new BasicDBObject([compartments: [$in: compartments]])
			return SearchParamHandler.andList([clauses, extra])
		}

		boolean getAccessIsRestricted() {
			return !isAdmin
		}

	}

	def evaluate(request){
		// If auth is disabled, treat everyone as an admin
		if (!grailsApplication.config.fhir.oauth.enabled) {
			request.authorization = new Authorization(isAdmin: true)
			return true
		}
		
		println "HEADER: "+ request.getHeader("Authorization")
		

		def basicAuthAccess = asBasicAuth(request)
		if (basicAuthAccess) {
			request.authorization = basicAuthAccess
			return true
		}

		def bearerAuthAccess = asBearerAuth(request)
		if (bearerAuthAccess) {
			request.authorization = bearerAuthAccess
			return true
		}

		return false
	}
}
