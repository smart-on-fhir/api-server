package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.JsonComposer
import org.hl7.fhir.instance.formats.JsonParser

class FhirJsonCodec  {
	static decode = { str ->
		new JsonParser().parse(IOUtils.toInputStream(str));
	}
	static encode = { resource ->
		ByteArrayOutputStream jsonStream = new ByteArrayOutputStream()
		new JsonComposer().compose(jsonStream,resource, true)
		jsonStream.toString()
	}
  }
