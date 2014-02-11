import java.io.File
import fhir.SqlService

SqlService sqlService = ctx.sqlService

String todo = new File('postgres-tables.sql').text
println ("Creating SQL tables...")
sqlService.sql.execute(todo)
println "Creation complete."
