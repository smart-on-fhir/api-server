package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.JsonParser
import org.hl7.fhir.instance.formats.IParser

class FhirJsonCodec  {
  
  static IParser prettyParser = {
    JsonParser jp= new JsonParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    jp
  }.call()
    
  static decode = { str ->
    def ret = prettyParser.parse(IOUtils.toInputStream(str));
    return ret
  }
  static encode = { resource ->
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream()
    prettyParser.compose(jsonStream,resource)
    jsonStream.toString()
  }
}
