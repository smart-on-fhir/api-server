// Place your Spring DSL code here
import fhir.auth.TokenCache

beans = {
	tokenCache(TokenCache){
		spec = grailsApplication.config.fhir.oauth.tokenCacheSpec
	}
}
