
<%
 def urlService = grailsApplication.mainContext.getBean("urlService");
%>
<!DOCTYPE html>
<html>
<head>
<title>SMART on FHIR</title>
<link rel="shortcut icon"
	href="https://smarthealthit.org/wp-content/uploads/2012/09/favicon1.png">
<link
	href='https://fonts.googleapis.com/css?family=PT+Sans+Narrow:400,700'
	rel='stylesheet' type='text/css'>
<style type="text/css" media="screen">
html {
	background: url(static/images/fire-shot.jpg) no-repeat center center fixed;
	-webkit-background-size: cover;
	-moz-background-size: cover;
	-o-background-size: cover;
	background-size: cover;
}

body {
	margin: 0px;
}

a {
	color: rgb(253, 255, 241);
}

h1 {
	display: inline-block;
	padding: 30px;
	background: rgba(0, 0, 0, 0.7);
	width: 100%;
	box-sizing: border-box;
	-moz-box-sizing: border-box;
	font-size: 62px;
	color: rgb(253, 255, 241);
	font-family: 'PT Sans Narrow', sans-serif;
	font-weight: 800;
}

h2 {
	display: inline-block;
	padding: 10px;
	background: rgba(0, 0, 0, 0.7);
	font-size: 32px;
	color: rgb(253, 255, 241);
	font-family: 'PT Sans Narrow', sans-serif;
	font-weight: 400;
}
</style>
</head>
<body>
	<h1>SMART on FHIR</h1>
	<h2>
		Service URL: <strong>
			${urlService.fhirBase}
		</strong><br> Source: <strong><a
			href="https://github.com/smart-on-fhir/api-server">GitHub</a></strong><br>
	</h2>

</body>
</html>

