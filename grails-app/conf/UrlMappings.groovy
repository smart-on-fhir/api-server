class UrlMappings {

	static mappings = {
		
		name resourceInstance: "/fhir/$resource/@$id" {
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}
		
		name resourceClass: "/fhir/$resource"(controller: "Api") {
			action = [GET: "search", POST: "create"]
		}

		
		name resourceVersion: "/fhir/$resource/@$id/history/@$vid"(controller: "Api") {
			action = [GET: "vread"]
		}

		
		name searchResource: "/fhir/$resource/search"(controller: "Api") {
			action = [GET: "search", POST: "search"]
		}

//		GET [service-url]/[resourcetype]/(?parameters)
//  GET [service-url]/[resourcetype]/search(?parameters) (&_format=mimeType)

		
		"/"(view:"/index")
		"500"(view:'/error')
	}
}
