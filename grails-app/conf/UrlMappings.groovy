import fhir.AuthorizationException
import fhir.ResourceDeletedException


class UrlMappings {

	static excludes = ['/css/*','/images/*', '/js/*', '/favicon.ico']
	static mappings = {

		name base: "/" {
			controller="Api"
			action=[GET: "welcome", OPTIONS: "conformance", POST: "transaction", DELETE: "delete"]
		}

		name metadata: "/metadata"(controller: "Api") {
			action = [GET: "conformance"]
		}

		name summary: "/BlueButtonSummary" {
			controller="Api"
			action=[GET: "summary"]
		}

		name resourceInstance: "/$resource/$id" {
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}

		name resourceVersion: "/$resource/$id/_history/$vid"(controller: "Api") {
			action = [GET: "vread"]
		}

		name resourceClass: "/$resource"(controller: "Api") {
			action = [GET: "search", POST: "create"]
		}

		name summary: "/_history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceHistory: "/$resource/_history" {
			controller="Api"
			action=[GET: "history"]
		}

		name resourceInstanceHistory: "/$resource/$id/_history" {
			controller="Api"
			action=[GET: "history"]
		}


		"401"(controller: 'error', action: 'status401')
		"500"(controller: 'error', action: 'deleted', exception: ResourceDeletedException)
		"500"(controller: 'error', action: 'status405', exception:BundleValidationException)
		"500"(controller: 'error', action: 'status401', exception:AuthorizationException)
		"500"(controller: 'error', action: 'status500')

	}
}
