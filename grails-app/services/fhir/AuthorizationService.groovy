package fhir;

import com.mongodb.BasicDBObject
import grails.plugins.rest.client.RestBuilder
import fhir.searchParam.SearchParamHandler
import org.hl7.fhir.instance.model.Patient

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
  Map localAuth


  @PostConstruct
  void init() {
    oauth = grailsApplication.config.fhir.oauth
	localAuth = grailsApplication.config.localAuth
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
    if(header){
      log.debug("exampine an admin access password")
      
      String[] decoded = new String(header[0][1].decodeBase64()).split(':')
      log.debug("try an admin access password" + decoded)
      if (decoded[0] == localAuth.clientId && decoded[1] == localAuth.clientSecret) {
        def ret = new Authorization(isAdmin: true, app: localAuth.clientId)
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
      def status = tokenCache.cache.get(token, { lookup(token) })
      log.debug("remapping: $status")

      // Token introspection param names have changed; support old + new
     /* def mappedStatus = [
        active: status.active ?: status.valid,
        exp: status.exp ?: status.expires_at,
        sub: status.sub ?: status.subject,
        client_id: status.client_id,
        scope: status.scope
      ]*/

  //    status = mappedStatus
      log.debug("Status: $status")

      if (!status.active) return null;
      Date exp = org.joda.time.format.ISODateTimeFormat.dateTimeParser()
          .parseDateTime(status.exp).toDate()

      if (status.scope.class == String) status.scope = status.scope.split("\\s+") as List
      
      // TODO: make *.read produce read-only permissions
      def ret = new Authorization(
          isAdmin: "fhir_complete" in status.scope || "user/*.*" in status.scope || "user/*.read" in status.scope,
          isActive:status.active,
          expiration: exp,
          username: status.sub,
          app: status.client_id)
      
      println "is admin ${ret.isAdmin} because ${status.scope} + ${status.scope.class}"
      
      ret.scopes = status.scope
      
      ret.compartments =   status.scope.collect {
        def m = (it =~ /(summary|search):(.*)/)
        if (!m.matches()) return null
        return "Patient/" +  (m[0][2] ?: "example")
      }.findAll { it != null}
      
      if (status.patient) {
        ret.compartments += "Patient/"+status.patient
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
    List<String> scopes = []
    List<String> compartments = []

    boolean allows(p) {
      // String operation, Class resource, List<String> compartmentsToCheck
      if (isAdmin) return true
      if (!isActive) return false

      p.compartments.every{ String c -> c in compartments }
    }
    
    boolean hasScope(String s){
      return isAdmin || scopes.contains(s);
    }

    void assertScope(String s) {
      if (isAdmin) return
      if (!hasScope(s))
          throw new AuthorizationException("Unauthorized:  Need scope $s but you only have " + scopes)
    }
    
    void assertAccessAny(p) {
      if (isAdmin || p.compartments.empty) return
        println(" P's compartments" + p.compartments.properties)

        if (!(p.compartments as List).any {it in compartments})
          throw new AuthorizationException("Unauthorized:  Need any one of ${p.compartments} but you only have " + compartments)
    }

    void assertAccessEvery(p) {
      if (isAdmin || p.compartments.empty) return

        if (!(p.compartments as List).every {it in compartments})
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
    println("Evaluating request $request with " + request.params)
    // If auth is disabled, treat everyone as an admin
    if (!grailsApplication.config.fhir.oauth.enabled) {
      request.authorization = new Authorization(isAdmin: true)
      //request.authorization = new Authorization(isAdmin: false)
      //request.authorization.compartments = ['Patient/123']
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
