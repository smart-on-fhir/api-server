grails {
	mongo {
		databaseName="fhir"
	}
}

environments { 
   production { 
       grails { 
            mongo { 
               databaseName="fhir"
               host = System.env.MONGO_HOST
               port = System.env.MONGO_PORT ?: 27017
               username = System.env.MONGO_USERNAME
               password = System.env.MONGO_PASSWORD
           } 
       } 
   } 
} 
