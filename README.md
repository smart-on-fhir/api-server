smart-on-fhir
=============

Open-source FHIR Server to support patient- and clinician-facing apps.
Still highly experimental and highly unstable, but has limited support for:

 * GET, POST, and PUT resources
 * Search for current resources based on FHIR's defined search params

## Installing

### Prerequisites
* Download and install [Grails 2.2.4](http://grails.org/download)
* Install MongoDB (locally or use a remote service)

###  Running
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
Add new data to the server via HTTP POST.  For example, with default
authentication settings and a server running at http://localhost:9090, you can add a new Diagnostic Order via:

```
curl 'http://localhost:9090/fhir/diagnosticorder/@example' \
     -X PUT \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA=='\
     -H 'Content-Type: text/xml' \
     --data @grails-app/conf/examples/diagnosticorder.xml
```

And then you can retrieve a feed of diagnostic orders via:

```
curl 'http://localhost:9090/fhir/diagnosticorder/search' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA=='
```

or fetch a single resource as JSON via:

```
curl 'http://localhost:9090/fhir/diagnosticorder/@example' \
     -H 'Authorization: Basic Y2xpZW50OnNlY3JldA==' \
     -H 'Accept: application/json'
```

There's very rudimentary support for adding C-CDA documents to the server,
with a client-side loader script. The loader will:
 
 * Store the raw content of your C-CDA
 * Create a FHIR DocumentReference for your C-CDA, with a `location` pointing to the raw content.
 * Create an empty patient if needed

 Here's how to invoke it (note the 
awkward use of environment variables to pass arguments):

```
BASE_URL="http://localhost:9090" \
CLIENT_ID=client \
CLIENT_SECRET=secret \
PATIENT_ID="1234" \
CCDA="grails-app/conf/examples/ccda.xml"\
grails run-script scripts/LoadCCDA.groovy
```

