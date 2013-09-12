import fhir.AuthorizationException


class UrlMappings {

	static mappings = {

		name base: "/fhir" {
			controller="Api"
			action=[OPTIONS: "conformance", POST: "transaction"]
		}

		name metadata: "/fhir/metadata"(controller: "Api") {
			action = [GET: "conformance"]
		}

		name summary: "/fhir/summary" {
			controller="Api"
			action=[GET: "summary"]
		}

		name resourceInstance: "/fhir/$resource/@$id" {
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}

		name resourceVersion: "/fhir/$resource/@$id/history/@$vid"(controller: "Api") {
			action = [GET: "vread"]
		}

		name resourceClass: "/fhir/$resource"(controller: "Api") {
			action = [GET: "search", POST: "create"]
		}


		name searchResource: "/fhir/$resource/search"(controller: "Api") {
			action = [GET: "search", POST: "search"]
		}


		"/"(view:"/index")

		"401"(controller: 'error', action: 'status401')
		"500"(controller: 'error', action: 'status405', exception:BundleValidationException)
		"500"(controller: 'error', action: 'status401', exception:AuthorizationException)
        "500"(view:'/error')
	}
}
