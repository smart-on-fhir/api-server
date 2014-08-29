smart-on-fhir
=============


Open-source [FHIR](http://hl7.org/implement/standards/fhir/) Server to support patient- and clinician-facing apps.

Still highly experimental, but has limited support for:

 * GET, POST, and PUT resources
 * `transaction` (POST a bundle of resources)
 * Search resources based on FHIR's defined search params

## Live demo: [API](https://fhir-api.smartplatforms.org) | [Apps](https://fhir.smartplatforms.org)

## Installing

### Prerequisites
* Download and install [Grails](http://grails.org/download)
* Install Postgres 9.1+ (locally or use a remote service)
* Oracle Java 7 JDK (not JRE -- and **not Java 8**)

###  Get it
```
$ git clone https://github.com/smart-on-fhir/api-server
$ cd api-server
```

### Initialize the DB (see config below as needed)
Ensure that `/etc/postgresql/9.1/main/pg_hba.conf` contains a line like:

```
local   all         all                               md5
```
(If you have `local all all peer`, for example, `peer` with `md5`.)



```
$ sudo -u postgres -i
postgres@$ createuser -R  -P -S  -D fhir
           [at password prompt: fhir]
postgres@$ createdb -O fhir fhir
postgres@$ logout
$ grails compile
$ grails -DnoTomcat=true run-script scripts/CreateDatabase.groovy
```

### Run it
```
$ grails run-app
```

## Configuring
Key settings files are:

#### grails-app/conf/Config.groovy
* Turn authentication or off with `fhir.oauth.enabled`: `true | false`
* Configure authentication with `fhir.oauth`

#### grails-app/conf/DataSource.groovy
* Configure your Postgres `dataSource`

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
curl 'http://localhost:8080/DiagnosticOrder' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

or fetch a single resource as JSON via:

```
curl 'http://localhost:8080/DiagnosticOrder/example' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA==' \
     -H 'Accept: application/json'
```

## Getting more sample data
You can load sample data from SMART's [Sample Patients](https://github.com/chb/smart_sample_patients/tree/fhir):

```
$ sudo apt-get install python-jinja2
$ git clone --recursive https://github.com/smart-on-fhir/sample-patients
$ cd sample-patients/bin
$ git checkout fhir
$ python generate.py --write-fhir ../generated-data
$ ls ../generated-data # a bunch of XML files
```

### Loading these files into your system

```
cd ../generated-data
for i in *.xml; do 
   curl 'http://localhost:8080/?' \
        -H 'Content-Type: text/xml' \
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

To load a collection of >600 C-CDA documents (and a FHIR `DocumentReference` for each), you can do:

```
cd load-emerge-patients
git clone https://github.com/chb/sample_ccdas
./gradlew -PemergeDir=sample_ccdas/EMERGE/ -PfhirBase="http://localhost:8080" loadPatients

```

And load a single patient with id of `example`:
```
./gradlew -PemergeDir=../grails-app/conf/examples -PfhirBase="http://localhost:8080" loadPatients
```


