FROM tomcat:8.0

COPY target/*.war /usr/local/tomcat/webapps/


# If you supply a tomcat-users.xml you can uncomment this line
# it will move it to the appropriate location.
#COPY tomcat-users.xml conf/tomcat-users.xml
