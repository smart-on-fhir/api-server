SMART on FHIR
=============


Open-source [FHIR](http://hl7.org/implement/standards/fhir/) Server to support patient- and clinician-facing apps.

Still highly experimental, but has limited support for:

 * GET, POST, and PUT resources
 * `transaction` (POST a bundle of resources)
 * Search resources based on FHIR's defined search params

## Live demo: [API](https://fhir-api.smarthealthit.org) | [Apps](https://fhir.smarthealthit.org)

## Installing

### Prerequisites
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
```

### Run it
```
$ ./grailsw run-app
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
You can load sample data from SMART's [Sample Patients](https://github.com/smart-on-fhir/sample-patients):

```
$ git clone --recursive https://github.com/smart-on-fhir/sample-patients
$ cd sample-patients/bin
$ pip install -r requirements.txt
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

