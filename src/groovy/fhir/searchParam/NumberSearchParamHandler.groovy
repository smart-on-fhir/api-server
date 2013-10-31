package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import com.mongodb.BasicDBObject;

// Need clarification about how this is different from other
// numerical types (double, say).
public class NumberSearchParamHandler extends StringSearchParamHandler {}

