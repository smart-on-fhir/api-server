import fhir.AuthorizationException
import fhir.ResourceDeletedException


class UrlMappings {

	static mappings = {

		name base: "/" {
			controller="Api"
			action=[GET: "welcome", OPTIONS: "conformance", POST: "transaction", DELETE: "delete"]
		}

		name metadata: "/metadata"(controller: "Api") {
			action = [GET: "conformance"]
		}

		name summary: "/summary" {
			controller="Api"
			action=[GET: "summary"]
		}

		name resourceInstance: "/$resource/@$id" {
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}

		name resourceVersion: "/$resource/@$id/history/@$vid"(controller: "Api") {
			action = [GET: "vread"]
		}

		name resourceClass: "/$resource"(controller: "Api") {
			action = [GET: "search", POST: "create"]
		}

		name searchResource: "/$resource/search"(controller: "Api") {
			action = [GET: "search", POST: "search"]
		}

		name summary: "/history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceHistory: "/$resource/history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceInstanceHistory: "/$resource/@$id/history" {
			controller="Api"
			action=[GET: "history"]
		}


		"401"(controller: 'error', action: 'status401')
		"500"(controller: 'error', action: 'deleted', exception: ResourceDeletedException)
		"500"(controller: 'error', action: 'status405', exception:BundleValidationException)
		"500"(controller: 'error', action: 'status401', exception:AuthorizationException)
		"500"(view:'/error')
	}
}
