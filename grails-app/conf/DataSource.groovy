/*dataSource {
    pooled = true

}*/
dataSource {
	pooled = true
    driverClassName = "org.postgresql.Driver"
    dialect = org.hibernate.dialect.PostgresDialect
	
    url = "jdbc:postgresql://localhost/fhir"
    username = "fhir"
    password = "fhir"
	dbCreate = "update"
}


hibernate { }

environments { 
   development { 
   } 
} 
