smart-on-fhir
=============


Open-source [FHIR](http://hl7.org/implement/standards/fhir/) Server to support patient- and clinician-facing apps.

Still highly experimental, but has limited support for:

 * GET, POST, and PUT resources
 * `transaction` (POST a bundle of resources)
 * Search resources based on FHIR's defined search params

## Live demo: [API](https://api.fhir.me) | [Apps](https://apps.fhir.me)

## Installing

### Prerequisites
* Download and install [Grails 2.2.4](http://grails.org/download)
* Install MongoDB (locally or use a remote service)

###  Run it (using default config)
```
$ git clone https://github.com/jmandel/smart-on-fhir
$ cd smart-on-fhir
$ grails run-app
```

### Configuring
Key settings files are:

#### grails-app/conf/Config.groovy
* Turn authentication or off with `fhir.oauth.enabled`: `true | false`
* Configure authentication with `fhir.oauth`

#### grails-app/conf/DataSource.groovy
* Configure your MongoDB location with `grails.mongo`

## Using
Add new data to the server via HTTP PUT or POST.  For example, with default
authentication settings and a server running at http://localhost:8080, you can add a new Diagnostic Order via:

```
curl 'http://localhost:8080/DiagnosticOrder/example' \
     -X PUT \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA=='\
     -H 'Content-Type: text/xml' \
     --data @grails-app/conf/examples/diagnosticorder.xml
```

And then you can retrieve a feed of diagnostic orders via:

```
curl 'http://localhost:8080/DiagnostiCorder' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

or fetch a single resource as JSON via:

```
curl 'http://localhost:8080/DiagnosticOrder/example' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA==' \
     -H 'Accept: application/json'
```

## Getting more sample data
You can load sample data from SMART's [Sample Patietns](https://github.com/chb/smart_sample_patients/tree/fhir):

```
$ sudo apt-get install python-jinja2
$ git clone --recursive https://github.com/chb/smart_sample_patients
$ cd smart_sample_patients/bin
$ git checkout fhir
$ python generate.py --write-fhir ../generated
$ ls ../generated # a bunch of XML files
```

### Loading these files into your system

```
cd ../generated
for i in *.xml; do 
   curl 'http://localhost:8080/?' 
        -H 'Content-Type: text/xml'
        --data-binary @$i; 
done
```

## Storing `Document`s + `DocumentReference`s
There's very rudimentary support for adding C-CDA documents to the server,
with a client-side loader script. The loader will:
 
 * Store the raw content of your C-CDA
 * Create a FHIR DocumentReference for your C-CDA, with a `location` pointing to the raw content.
 * Create an empty patient if needed

Here's how to invoke it (note the awkward use of environment variables to pass arguments):

```
BASE_URL="http://localhost:8080" \
CLIENT_ID=client \
CLIENT_SECRET=secret \
PATIENT_ID="1234" \
CCDA="grails-app/conf/examples/ccda.xml"\
grails run-script scripts/LoadCCDA.groovy
```


## Loading EMERGE Test Patients

To load a collection of 300 C-CDA documents (and a FHIR `DocumentReference` for each), you can do:

```
cd load-emerge-patients
git clone https://github.com/chb/sample_ccdas
./gradlew -PemergeDir=sample_ccdas/EMERGE/ -PfhirBase="http://localhost:8080" loadPatients

```


