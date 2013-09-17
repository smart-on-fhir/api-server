grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6
grails.project.source.level = 1.6


//grails.project.war.file = "target/${appName}-${appVersion}.war"

// uncomment (and adjust settings) to fork the JVM to isolate classpaths
//grails.project.fork = [
//   run: [maxMemory:1024, minMemory:64, debug:false, maxPerm:256]
//]

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve
    legacyResolve false // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        inherits true // Whether to inherit repository definitions from plugins

        grailsPlugins()
        grailsHome()
        grailsCentral()

        mavenLocal()
        mavenCentral()

        // uncomment these (or add new ones) to enable remote dependency resolution from public Maven repositories
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
    }

    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes e.g.
		runtime 'com.google.guava:guava:14.0.1'
		runtime "org.json:json:20090211"
		runtime "com.google.code.gson:gson:2.2.4"
		runtime "joda-time:joda-time:2.2"
		runtime 'org.apache.commons:commons-io:1.3.2'
		runtime "org.mongodb:mongo-java-driver:2.11.2"
		runtime 'xpp3:xpp3:1.1.3.4.O'
		runtime 'xmlpull:xmlpull:1.1.3.4d_b4_min'
		runtime 'net.sf.saxon:Saxon-HE:9.4'
		runtime('org.codehaus.groovy.modules.http-builder:http-builder:0.5.2') {
			excludes 'groovy'
			excludes 'xml-apis'
			excludes 'xalan'
		}
    }

    plugins {
		compile ":rest-client-builder:1.0.2"
		compile ":mongodb:1.3.0"
        runtime ":jquery:1.8.3"
        runtime ":resources:1.2"
		runtime ":cors:1.1.0"

        // Uncomment these (or add new ones) to enable additional resources capabilities
        //runtime ":zipped-resources:1.0"
        //runtime ":cached-resources:1.0"
        //runtime ":yui-minify-resources:0.1.5"

        build ":tomcat:$grailsVersion"

        runtime ":database-migration:1.3.2"

        compile ":cache:1.0.1"
		compile ":standalone:1.2.1"
    }
}
