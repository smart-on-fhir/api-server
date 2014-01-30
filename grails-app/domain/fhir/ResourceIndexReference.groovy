package fhir

class ResourceIndexReference extends ResourceIndexTerm{
  String reference_type
  String reference_id
  String reference_version
  String reference_is_external

  static mapping = { version false }
  static constraints = {
    reference_id nullable: true
    reference_type nullable: true
    reference_version nullable: true
  }
}

