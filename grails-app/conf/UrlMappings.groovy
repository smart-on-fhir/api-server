class UrlMappings {

	static mappings = {

		name summary: "/fhir/summary" {
			controller="Api"
			action=[GET: "summary"]
		}

		name resourceInstance: "/fhir/$resource/@$id/$rawData?" {
		    raw = {params.rawData == "raw"}
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}

		name resourceVersion: "/fhir/$resource/@$id/history/@$vid/$rawData?"(controller: "Api") {
			raw = {params.rawData == "raw"}
			action = [GET: "vread"]
		}

		name resourceClass: "/fhir/$resource"(controller: "Api") {
			action = [GET: "search", POST: "create"]
		}


		name searchResource: "/fhir/$resource/search"(controller: "Api") {
			action = [GET: "search", POST: "search"]
		}


		"/"(view:"/index")
		"500"(view:'/error')
	}
}
