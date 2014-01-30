package fhir

class ResourceIndexNumber extends ResourceIndexTerm{
  float number_min
  float number_max

  static mapping = { version false }
}