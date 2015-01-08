package fhir
import org.apache.commons.io.IOUtils
import org.hl7.fhir.instance.formats.IParser;
import org.hl7.fhir.instance.formats.XmlParser

class FhirXmlCodec  {
  
  
  static decode = { str ->
    XmlParser jp= new XmlParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    jp.parse(IOUtils.toInputStream(str));
  }

  static encode = { resource ->
    XmlParser jp= new XmlParser()
    jp.outputStyle = IParser.OutputStyle.PRETTY
    ByteArrayOutputStream xmlStream = new ByteArrayOutputStream()
    jp.compose(xmlStream,resource)
    xmlStream.toString()
  }
}
