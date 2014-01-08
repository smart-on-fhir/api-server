import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.AtomEntry
import org.hl7.fhir.instance.model.AtomFeed
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Period
import org.hl7.fhir.instance.model.CodeableConcept
import org.hl7.fhir.instance.model.Coding
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.DateAndTime
import org.hl7.fhir.instance.model.Identifier
import org.hl7.fhir.instance.model.ResourceReference
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.formats.XmlComposer
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import org.xmlpull.v1.XmlPullParserFactory;
import org.joda.time.format.ISODateTimeFormat
import groovy.xml.MarkupBuilder
import groovy.transform.Field

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

import groovy.io.FileType


import groovyx.net.http.HTTPBuilder

import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.auth.BasicScheme

fhirBase = System.env.BASE_URL // e.g. "http://localhost:8001";
String filePath = System.env.BASE_DIR // e.g. '/home/jmandel/smart/sample_ccdas/EMERGE';
username = System.env.USERNAME
password = System.env.PASSWORD


fhir = [:]
fhir.namespaces = [ f: "http://hl7.org/fhir", xhtml: "http://www.w3.org/1999/xhtml" ];


AtomFeed feed = new AtomFeed();
def allFiles = []
new File(filePath).eachFileRecurse (FileType.FILES) { file ->
   allFiles << file
}

allFiles.each {
   def m = it.path.toString() =~ /.*Patient-(.*)\.xml/;
   if (!m.matches()) return;
   processOneFile(it, m[0][1]); 
}

Resource makePatient(){
   def patientWriter = new StringWriter();
   def p = new MarkupBuilder(patientWriter);
   p.Patient('xmlns':fhir.namespaces.f,
         'xmlns:xhtml':fhir.namespaces.xhtml) {
      text() {
         status(value:'generated');
         'xhtml:div'("A BB+ FHIR Sample Patient -- see DocumentReference elements for data.");
      }
   };
   return toResource(patientWriter.toString());
}

String toXml(def resource){
   ByteArrayOutputStream xmlStream = new ByteArrayOutputStream()
      new XmlComposer().compose(xmlStream, resource, true)
      xmlStream.toString()
}

Resource toResource(String str){
   def ret = new XmlParser().parseGeneral(IOUtils.toInputStream(str));
   return ret.resource ?: ret.feed
}


def processOneFile(File file, String pid) {
   byte[] bytes = Files.readAllBytes(Paths.get(file.path))
   println("Posting a new C-CDA");
   println("Patient ID: " + pid);

   Resource p = makePatient();

   def x = new groovy.util.XmlParser().parse(file);
   def f = new groovy.xml.Namespace('http://hl7.org/fhir');
   def h = new groovy.xml.Namespace('urn:hl7-org:v3');

   DocumentReference doc = new DocumentReference();

   def subject = new ResourceReference();
   subject.referenceSimple = "Patient/$pid";

   doc.subject = subject;

   def masterIdentifier = new Identifier();
   masterIdentifier.systemSimple = x.id[0].@root;
   masterIdentifier.valueSimple = x.id[0].@extension;
   masterIdentifier.labelSimple = "Document ID ${masterIdentifier.systemSimple}" + 
      (x.id[0].@extension ? "/${masterIdentifier.valueSimple}" : "");

   doc.masterIdentifier = masterIdentifier;

   Map systems = [ '2.16.840.1.113883.6.1': 'http://loinc.org',
       '2.16.840.1.113883.6.96': 'http://snomed.info/id' ];

   
   doc.context = {
	   def c = new DocumentReference.DocumentReferenceContextComponent();
       def dateParser = ISODateTimeFormat.basicDate();
       def dateFormatter = ISODateTimeFormat.date();
	   c.period = {
		   def period = new Period();
           def start = dateParser.parseDateTime(x.documentationOf.serviceEvent.effectiveTime.low[0].@value[0..7])
       	   def end = dateParser.parseDateTime(x.documentationOf.serviceEvent.effectiveTime.high[0].@value[0..7])
			 
		   // lie about the start time because EMERGE has a patient's birthdate here by mistake
		   period.startSimple = new DateAndTime(dateFormatter.print(end.minus(24 * 60 * 60  * 1000)));
		   period.endSimple = new DateAndTime(dateFormatter.print(end));
		   return period 
	   }()
	   return c
   }()

   def type = new CodeableConcept();
   doc.type = type;
   type.coding = []

   def typeCoding = new Coding();
   type.coding.add(typeCoding);

   if (x.code[0]?.@codeSystem) {
      typeCoding.systemSimple = systems[x.code[0].@codeSystem] ?: x.code[0].@codeSystem;
      typeCoding.codeSimple = x.code[0].@code;
      typeCoding.displaySimple = x.code[0].@displayName;
   } else {
      typeCoding.displaySimple = "Unknown";
   }

   if (typeCoding.codeSimple == "34133-9") {
      type.coding.add({
      typeCoding = new Coding();
      typeCoding.codeSimple = "Summary"
      typeCoding.displaySimple = "Continuity of Care Document"
	  return typeCoding
      }())
   }

   def contained =  [];
   x.author.assignedAuthor.assignedPerson.each {
      def author = new ResourceReference()
         def name =  it.name.given.collect{it.text()}.join(" ")+ " " +  it.name.family.text();
      author.displaySimple = name;
      doc.author.add(author);

      println it.name + " then given " + it.name.given.collect{it.text()};

   }

   doc.indexedSimple = DateAndTime.now();
   doc.statusSimple = DocumentReference.DocumentReferenceStatus.current;
   doc.author.collect{it.displaySimple};

   doc.descriptionSimple = x.title.text();
   doc.mimeTypeSimple = "application/hl7-v3+xml";

   Binary rawResource = new Binary();
   rawResource.setContent(bytes);
   rawResource.setContentType(doc.mimeTypeSimple);
   println "making closure";
   def now = DateAndTime.now();

   AtomFeed feed = new AtomFeed();
/*
   AtomEntry patientRef = new AtomEntry();
   patientRef.id = "Patient/$pid";
   patientRef.resource = p;
   patientRef.updated = now;
*/
   AtomEntry rawEntry = new AtomEntry();
   rawEntry.id = "urn:cid:binary-document";
   rawEntry.resource = rawResource;
   rawEntry.updated = now;


   doc.locationSimple = rawEntry.id;
   AtomEntry rawDocRef = new AtomEntry();
   rawDocRef.id = "urn:cid:doc-ref";
   rawDocRef.resource = doc;
   rawDocRef.updated = now;

   //feed.entryList.add(patientRef);
   feed.entryList.add(rawEntry);
   feed.entryList.add(rawDocRef);

   feed.authorName = "groovy.config.atom.author-name";
   feed.authorUri  = "groovy.config.atom.author-uri";
   feed.id = "feed-id";
   feed.updated = now;

   HTTPBuilder rest = new HTTPBuilder( fhirBase );

   UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);
   def basic = BasicScheme.authenticate(creds, "UTF-8", false);
   
   def docPost  = rest.request(POST, XML) { req ->
	uri.query  = [compartments: "Patient/$pid"]
	headers[basic.name]  = basic.value
	headers.'Content-Type' = "application/xml"
	body = toXml(feed)   
   }

}
