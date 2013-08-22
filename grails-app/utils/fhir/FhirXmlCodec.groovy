package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.XmlComposer
import org.hl7.fhir.instance.formats.XmlParser

class FhirXmlCodec  {
	static decode = { str ->
		new XmlParser().parse(IOUtils.toInputStream(str));
	}
	
	static encode = { resource ->
		ByteArrayOutputStream xmlStream = new ByteArrayOutputStream()
		new XmlComposer().compose(xmlStream,resource, true)
		xmlStream.toString()
	}
	
  }
