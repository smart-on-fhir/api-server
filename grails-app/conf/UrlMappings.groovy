import fhir.AuthorizationException
import fhir.ResourceDeletedException


class UrlMappings {

	static mappings = {

		name base: "/fhir" {
			controller="Api"
			action=[OPTIONS: "conformance", POST: "transaction", DELETE: "delete"]
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

		name summary: "/fhir/history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceHistory: "/fhir/$resource/history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceInstanceHistory: "/fhir/$resource/@$id/history" {
			controller="Api"
			action=[GET: "history"]
		}

		"/"(view:"/index")

		"401"(controller: 'error', action: 'status401')
		"500"(controller: 'error', action: 'deleted', exception: ResourceDeletedException)
		"500"(controller: 'error', action: 'status405', exception:BundleValidationException)
		"500"(controller: 'error', action: 'status401', exception:AuthorizationException)
		"500"(view:'/error')
	}
}
