package fhir

class ResourceIndexToken extends ResourceIndexTerm{
  String token_namespace
  String token_code
  String token_text

  static mapping = { version false }
  static constraints = {
    token_text type: 'text', nullable: true
    token_namespace nullable: true, index:'search_token'
    token_code index:'search_token'
  }
}

