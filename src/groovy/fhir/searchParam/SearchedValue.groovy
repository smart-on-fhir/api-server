package fhir.searchParam

import java.util.Map;

//import org.hl7.fhir.instance.model.Conformance.SearchParamType;

// Simple name-value tuple to represent indexed Search Parameters.
// For example:
//     name="diagnosisDate", value="2000"
//	   name="diagnosisDate:before", value="2000-01-01T00:00:00Z"
class SearchedValue{
  public SearchParamHandler handler;
  public String modifier;
  public String values;
  public SearchedValue chained;
}
