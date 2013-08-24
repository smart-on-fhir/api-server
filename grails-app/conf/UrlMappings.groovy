class UrlMappings {

	static mappings = {

		name resourceInstance: "/fhir/$resource/@$id/$rawData?" {
		    raw = {println("checking craw: " + params); params.rawData == "raw"}
			controller="Api"
			action=[GET: "read", PUT: "update", DELETE: "delete"]
		}

		name resourceVersion: "/fhir/$resource/@$id/history/@$vid/$rawData?"(controller: "Api") {
			raw = {println("checking hraw: " + params); params.rawData == "raw"}
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
