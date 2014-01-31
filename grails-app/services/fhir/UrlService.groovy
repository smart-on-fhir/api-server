package fhir;



class UrlService{
  def transactional = false
  def grailsLinkGenerator
  def regex = /([^\/]+)\/([^\/]+)(?:\/_history\/([^\/]+))?/

  String fhirCombinedId(String p) {
    String ret = null
    Map parts = fhirUrlParts(p)
    if (parts.size() == 0) return ret
    return "${parts.type}/${parts.id}"
  }

  Map fhirUrlParts(String p) {
    Map ret = [:]
    def m = (p =~ regex)
    if (m.size()) {
      ret['type'] = m[0][1]
      ret['id'] = m[0][2]
      if (m.size() > 2) {
        ret['version'] = m[0][3]
      } else {
        ret['version'] = null
      }
    }
    return ret
  }

  String getBaseURI() {
    grailsLinkGenerator.link(uri:'', absolute:true)
  }

  String getDomain() {
    def m = baseURI =~ /(https?:\/\/[^\/]+)/
    return m[0][1]
  }

  String getFhirBase() {
    baseURI
  }

  URL getFhirBaseAbsolute() {
    new URL(fhirBase + '/')
  }

  String relativeResourceLink(String resource, String id) {
    "$resource/$id"
  }

  String resourceLink(String resourceName, String fhirId) {
    grailsLinkGenerator.link(
        mapping: 'resourceInstance',
        absolute: true,
        params: [
          resource: resourceName,
          id: fhirId
        ])
  }

  String resourceVersionLink(String resourceName, String fhirId, vid) {
    grailsLinkGenerator.link(
        mapping: 'resourceVersion',
        absolute: true,
        params: [
          resource: resourceName,
          id: fhirId,
          vid:vid.toString()
        ])
  }

  String fullRequestUrl(request) {
    return domain + request.forwardURI + '?' + (request.queryString ?: "")
  }
}
