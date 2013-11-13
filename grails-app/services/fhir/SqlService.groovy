package fhir;

import groovy.sql.Sql

class SqlService{

	def transactional = false
	def sessionFactory
	
	Sql getSql() {
		Sql.newInstance(sessionFactory.currentSession.connection())
	}	
	def rows(String q, Map params) {
		println("Params $params " + params.size())
		if (params.size() == 0) {
			return sql.rows(q)
		}
		sql.rows(q, params)
	}
}
