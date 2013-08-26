import grails.plugins.rest.client.RestBuilder
import groovy.xml.MarkupBuilder

import java.nio.file.Files
import java.nio.file.Paths

import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.CodeableConcept
import org.hl7.fhir.instance.model.Coding
import org.hl7.fhir.instance.model.DocumentReference
import org.hl7.fhir.instance.model.Identifier
import org.hl7.fhir.instance.model.ResourceReference


//def grailsApplication = ctx.getBean("grailsApplication")

def rest = new RestBuilder(connectTimeout:5000, readTimeout:2000)
def oauth = grailsApplication.config.fhir.oauth
def fhirBase = (grailsApplication.config.grails.serverURL?: 'http://localhost:9090') +'/fhir/'
println("Base: $fhirBase")
println("OA: $oauth")

println("intarget")
String pid = "123"

def patient = rest.get(fhirBase+"patient/@$pid") {
	auth(oauth.clientId, oauth.clientSecret)
}

println(""+ patient.status)
if (patient.status == 404) {

	def patientWriter = new StringWriter()
	def p = new MarkupBuilder(patientWriter)
	p.Patient(
			'xmlns':grailsApplication.config.fhir.namespaces.f,
			'xmlns:xhtml':grailsApplication.config.fhir.namespaces.xhtml) {
				text() {
					status(value:'generated')
					'xhtml:div'("A BB+ FHIR Sample Patient -- see documentreference elements for data.")
				}
			}
	def put = rest.put(fhirBase+"patient/@$pid?compartments=patient/@$pid") {
		auth(oauth.clientId, oauth.clientSecret)
		contentType "text/xml"
		body patientWriter.toString()
	}
	assert put.status == 201
}



String filePath = '/home/jmandel/smart/sample_ccdas/HL7 Samples/CCD.sample.xml'
byte[] bytes = Files.readAllBytes(Paths.get(filePath));

def x = new XmlParser().parse(filePath)
def f = new groovy.xml.Namespace('http://hl7.org/fhir')
def h = new groovy.xml.Namespace('urn:hl7-org:v3')

DocumentReference doc = new DocumentReference()

def subject = new ResourceReference()
subject.typeSimple = 'Patient'
subject.referenceSimple = "patient/@$pid"

doc.subject = subject

def masterIdentifier = new Identifier()
masterIdentifier.systemSimple = x.id[0].@root
masterIdentifier.keySimple = x.id[0].@extension
masterIdentifier.labelSimple = "Document ID ${masterIdentifier.systemSimple}/${masterIdentifier.keySimple}"
doc.masterIdentifier = masterIdentifier
String patientCompartment = '123'

Map systems = [
	'2.16.840.1.113883.6.1': 'http://loinc.org',
	'2.16.840.1.113883.6.96': 'http://snomed.info/id'
]

def type = new CodeableConcept()
typeCoding = new Coding()
type.coding = [typeCoding]
typeCoding.systemSimple = systems[x.code[0].@codeSystem] ?: x.code[0].@codeSystem
typeCoding.codeSimple = x.code[0].@code
typeCoding.displaySimple = x.code[0].@displayName
doc.type = type

def contained =  []
x.author.assignedAuthor.assignedPerson.each {
	def author = new ResourceReference()
	def name =  it.name.given.collect{it.text()}.join(" ")+ " " +  it.name.family.text()
	author.typeSimple = 'Practitioner'
	author.referenceSimple = '#author-'+contained.size()
	author.displaySimple = name
	doc.author.add(author)

	doc.contained.add("""<Practitioner xmlns="http://hl7.org/fhir" id="${author.referenceSimple[1..-1]}">    
        <name>
          <family value="${it.name.family.text()}"/> 
          <given value="${it.name.given[0].text()}"/>
        </name>
    </Practitioner>""".decodeFhirXml())

}

doc.indexedSimple = Calendar.instance
doc.statusSimple = DocumentReference.DocumentReferenceStatus.current
doc.author.collect{it.displaySimple}

doc.descriptionSimple = x.title.text()
doc.mimeTypeSimple = "application/hl7-v3+xml"

Binary rawResource = new Binary()
rawResource.setContent(bytes)
rawResource.setContentType(doc.mimeTypeSimple)

def binary  = rest.post(fhirBase+"binary?compartments=patient/@$pid") {
	auth(oauth.clientId, oauth.clientSecret)
	contentType "text/xml"
	body rawResource.encodeAsFhirXml()
}

println binary.headers.location[0]
doc.locationSimple = (binary.headers.location[0]+'/raw').split(fhirBase)[1]

def docPost  = rest.post(fhirBase+"documentreference?compartments=patient/@$pid") {
	auth(oauth.clientId, oauth.clientSecret)
	contentType "text/xml"
	body doc.encodeAsFhirXml()
}
println("Doc: " + docPost.status + docPost.headers.location[0])
