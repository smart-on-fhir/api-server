package fhir;

import groovy.sql.GroovyRowResult
import groovy.sql.Sql


class SqlService{

	def transactional = false
	def sessionFactory
    def dataSource

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


}
