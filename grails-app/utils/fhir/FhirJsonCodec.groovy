package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.JsonComposer
import org.hl7.fhir.instance.formats.JsonParser

class FhirJsonCodec  {
  static decode = { str ->
    def ret = new JsonParser().parseGeneral(IOUtils.toInputStream(str));
    return ret.resource ?: ret.feed
  }
  static encode = { resource ->
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream()
    new JsonComposer().compose(jsonStream,resource, true)
    jsonStream.toString()
  }
}
