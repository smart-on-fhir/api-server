package fhir;

import groovyx.net.http.URIBuilder

import java.util.regex.Pattern

import org.bson.types.ObjectId
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.instance.model.DomainResource
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.DateTimeType
import org.hl7.fhir.instance.model.Bundle.BundleType;
import org.hl7.fhir.instance.model.Bundle.BundleTypeEnumFactory;
import org.hl7.fhir.instance.model.Bundle.HTTPVerb;
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.Bundle.BundleEntrySearchComponent;
import org.hl7.fhir.instance.model.Bundle.SearchEntryMode;

import org.apache.commons.lang3.time.FastDateFormat;

class BundleValidationException extends Exception{
}

class BundleService{

  def transactional = false
  def searchIndexService
  def urlService

  void validateFeed(Bundle feed) {
    if (feed == null) {
      throw new BundleValidationException('Could not parse a bundle. Ensure you have set an appropriate content-type.')
    }

    if (feed.entry == null || feed.entry.size() == 0) {
      throw new BundleValidationException('Did not find any resources in the posted bundle.')
      return
    }
  }

  String getResourceName(Resource r) {
    r.class.toString().split('\\.')[-1]
  }

  Bundle createFeed(p) {
    def feedId = p.feedId
    def entries = p.entries
    def paging = p.paging

    Bundle feed = new Bundle()

    feed.total = paging.total
    feed.type = p.type ?: BundleType.SEARCHSET

    feed.addLink().setRelation("self").setUrl(feedId)
    if (paging._skip + paging._count < paging.total) {
      def nextPageUrl = nextPageFor(feedId, paging)
      feed.addLink().setRelation("next").setUrl(nextPageUrl)
    }
    
    feed.entry.addAll entries
    feed
  }

  String nextPageFor(String url, PagingCommand paging) {
    url = url.replaceAll(Pattern.quote("|"), "%7C")
    URIBuilder u = new URIBuilder(url)

    if ('_count' in u.query) {
      u.removeQueryParam("_count")
    }
    u.addQueryParam("_count", paging._count)

    if ('_skip' in u.query) {
      u.removeQueryParam("_skip")
    }
    u.addQueryParam("_skip", paging._skip + paging._count)

    return u.toString()
  }

  Bundle assignURIs(Bundle f) {

    Map assignments = [:]

    // Determine any entry IDs that
    // need reassignment.  That means:
    // 1. IDs that are URNs but not URLs (assign a new ID)
    // 2. IDs that are absolute links to resources on this server (convert to relative)
    // For IDs that already exist in our system, rewrite them to ensure they always
    // appear as relative URIs (relative to [service-base]

    f.entry.each { BundleEntryComponent e ->

      boolean needsAssignment = false
      Resource res = e.resource
      Class c = res.class
      String resourceType = res.resourceType.path
      
      if (e.request.method == HTTPVerb.POST) {
        String oldid = e.resource.id
        e.resource.id = new ObjectId().toString()
        
        if (oldid != null) {
          assignments[urlService.relativeResourceLink(resourceType, oldid)] = 
            urlService.relativeResourceLink(resourceType, e.resource.id)
        }
      }
      
    }

    def xml = f.encodeAsFhirXml()
    assignments.each {from, to ->
      log.debug("Replacing: $from -> $to")
      xml = xml.replaceAll(Pattern.quote("value=\"$from\""), to)
    }

    return xml.decodeFhirXml()
  }
}
