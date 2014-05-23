package fhir

import fhir.AuthorizationService.Authorization
import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONObject

class LaunchContextController {

  static scope = "singleton"

  def create() {
    Authorization auth = request.authorization;
    auth.assertScope("launch");

    JSONObject req = request.JSON;
    req.put

    LaunchContext c = new LaunchContext();
    c.created_by =  auth.app ?: "can't tell :/";
    c.client_id = req.getString("client_id")
    req.getJSONObject("parameters").each { k,v ->
      c.addToParams(new LaunchContextParam(param_name: k, param_value: v))
    }
    c.save(flush:true, failOnError: true)

    render c.asJson()
  }

  def read() {
    Authorization auth = request.authorization;
    auth.assertScope("launch");

    LaunchContext c = LaunchContext.read(params.launch_id)
    render c.asJson()

  }
}
