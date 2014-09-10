package fhir

class ResourceIndexNumber extends ResourceIndexTerm{
  float number_min
  float number_max

  static mapping = { version false }
  static constraints = {
    number_min nullable: true
    number_max nullable: true
  }
}
