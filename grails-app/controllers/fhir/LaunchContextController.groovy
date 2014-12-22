package fhir

import fhir.AuthorizationService.Authorization
import grails.converters.JSON

import org.codehaus.groovy.grails.web.json.JSONObject
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Patient

class LaunchContextController {

  static scope = "singleton"
  BundleService bundleService
  UrlService urlService
  SqlService sqlService

  def create() {
    Authorization auth = request.authorization;
    auth.assertScope("smart/orchestrate_launch");

    JSONObject req = request.JSON;
    req.put

    LaunchContext c = new LaunchContext();
    c.created_by =  auth.app ?: "can't tell :/";
    c.client_id = req.getString("client_id");
    c.username = auth.username;

    req.getJSONObject("parameters").each { k,v ->
      c.addToParams(new LaunchContextParam(param_name: k, param_value: v))
    }
    c.save(flush:true, failOnError: true)

    render c.asJson()
  }

  def read() {
    Authorization auth = request.authorization;
    auth.assertScope("smart/orchestrate_launch");

    LaunchContext c = LaunchContext.read(params.launch_id)
    println "Resolved launch context ${c.asJson()}"
    render c.asJson()
  }

  def recentPatients() {
    Authorization auth = request.authorization;
    String username = auth.username;

    Map entries = LaunchContext
        .findAllByUsername(username, [max: 10, sort: "created_at", order: "desc"])
        .collectMany { LaunchContext lc ->
          lc.params
              .findAll { it.param_name == "patient" }
              .collect { it.param_value }
        } .collect {
          sqlService.getLatestByFhirId("Patient", it)
        } .collectEntries {
          [(it.fhir_type+'/'+it.fhir_id): it.content.decodeFhirJson()]
        }

    Bundle feed = bundleService.createFeed([
      entries: entries,
      paging: new PagingCommand(total: entries.size(), _count: entries.size(), _skip: 0),
      feedId: urlService.fullRequestUrl(request)
    ])

    request.resourceToRender = feed
  }
}
