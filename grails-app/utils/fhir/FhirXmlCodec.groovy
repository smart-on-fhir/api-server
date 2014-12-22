package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.IParser;
import org.hl7.fhir.instance.formats.XmlParser

class FhirXmlCodec  {
  
  static IParser prettyParser = {
    XmlParser jp= new XmlParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    jp
  }.call()
   
  
  static decode = { str ->
    def ret = prettyParser.parse(IOUtils.toInputStream(str));
    return ret
  }

  static encode = { resource ->
    ByteArrayOutputStream xmlStream = new ByteArrayOutputStream()
    prettyParser.compose(xmlStream,resource)
    xmlStream.toString()
  }
}
