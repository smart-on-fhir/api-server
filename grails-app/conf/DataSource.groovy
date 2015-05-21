/*dataSource {
    pooled = true

}*/
dataSource {
	pooled = true
    driverClassName = "org.postgresql.Driver"
    dialect = "net.kaleidos.hibernate.PostgresqlExtensionsDialect"
    url = System.env.DB ?: "jdbc:postgresql://localhost/fhir"
    username = System.env.PGUSER ?:"fhir"
    password = System.env.PGPASSWORD ?:"fhir"
    dbCreate = "update"

	properties {
		maxActive = 50
		maxIdle = 25
		minIdle = 1
		initialSize = 1

		numTestsPerEvictionRun = 3
		maxWait = 10000

		testOnBorrow = true
		testWhileIdle = true
		testOnReturn = true

		validationQuery = "select 1"

		minEvictableIdleTimeMillis = 1000 * 60 * 5
		timeBetweenEvictionRunsMillis = 1000 * 60 * 5
	 }
}


hibernate { }

environments {
   development {
   }
}
