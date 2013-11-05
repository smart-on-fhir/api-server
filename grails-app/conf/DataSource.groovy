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
	dbCreate = "create"
}


hibernate {
    cache.use_second_level_cache = true
    cache.use_query_cache = false
	cache.region.factory_class = 'org.hibernate.cache.ehcache.EhCacheRegionFactory' // Hibernate 4
}

environments { 
   development { 
   } 
} 
