package fhir

import org.apache.tools.ant.types.resources.comparators.ResourceComparator;

class ResourceVersion {

  long version_id
  String fhir_id
  String fhir_type

  Date rest_date = new Date()
  String rest_operation

  String content

  List<String> getCompartments(){
    ResourceCompartment c = ResourceCompartment.find("from ResourceCompartment as c where c.fhir_type=? and c.fhir_id=?", [fhir_type, fhir_id])
    return c.compartments as List
  }

  static mapping = {
    content type: 'text'
    table 'resource_version'
    id name: 'version_id'
    version false
    fhir_type index:'logical_id_index'
    fhir_id index:'logical_id_index'
  }

}
