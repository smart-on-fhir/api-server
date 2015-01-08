package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.JsonParser
import org.hl7.fhir.instance.formats.IParser

class FhirJsonCodec  {
  

  static decode = { str ->
    JsonParser jp= new JsonParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    jp.parse(IOUtils.toInputStream(str));
  }
  static encode = { resource ->
    JsonParser jp= new JsonParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream()
    jp.compose(jsonStream,resource)
    jsonStream.toString()
  }
}
