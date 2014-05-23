/*dataSource {
    pooled = true

}*/
dataSource {
	pooled = true
    driverClassName = "org.postgresql.Driver"
    dialect = "net.kaleidos.hibernate.PostgresqlExtensionsDialect"
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
