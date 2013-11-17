package fhir;


import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.Binary
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.DocumentReference

import fhir.AuthorizationService.Authorization
import groovy.sql.GroovyRowResult
import groovy.sql.Sql


class SqlService{

	def dataSource
	def urlService

	Sql getSql() {
		new Sql(dataSource)
	}

	List<GroovyRowResult> rows(String q, Map params) {
		println("Params $params " + params.size())
		if (params.size() == 0) {
			return sql.rows(q)
		}
		sql.rows(q, params)
	}


	ResourceVersion getLatestByFhirId(String fhir_type, fhir_id) {
		def ret = rows("""
				  select * from resource_version v  where 
                 	  v.fhir_id=:fhir_id and 
			       	  v.fhir_type=:fhir_type
			       	  order by v.version_id desc
			      	 limit 1 """, [fhir_id: fhir_id, fhir_type: fhir_type])
		if (ret.size() == 0) return null
		return ret[0]
	}

	Resource getLatestSummary(Authorization a) {

		def q = """select min(content) as content from resource_version where (fhir_type, fhir_id) in (
						select fhir_type, fhir_id from resource_compartment where fhir_type='DocumentReference'
						and compartments && """ +a.compartmentsSql+"""
					) group by fhir_type, fhir_id order by max(version_id) desc limit 1"""
		def content = rows(q, [:])
		if (!content) return null

		DocumentReference r = content[0].content.decodeFhirJson()
		Map location = urlService.fhirUrlParts(r.locationSimple)
		return getLatestByFhirId(location.type, location.id).content.decodeFhirJson()
	}
}
