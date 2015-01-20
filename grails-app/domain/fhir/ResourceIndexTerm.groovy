package fhir

class ResourceIndexTerm {
  long version_id
  String fhir_id
  String fhir_type
  String search_param

  static mapping = { 
    version false 
    fhir_id index: 'resource_index'
    fhir_type index: 'resource_index,searchparam_index,search_token'
    search_param index: 'searchparam_index,search_string_index,search_token'
    version_id index:'version_index'
   }

  GString insertStatement(versionId){

    def fields = GString.EMPTY + "version_id, class, fhir_type, fhir_id, search_param"
    def values = GString.EMPTY + "$versionId, ${this.class.name.split('\\.')[-1]}, ${fhir_type}, ${fhir_id}, ${search_param}"

    [
      'string_value',
      'composite_value',
      'date_min',
      'date_max',
      'token_code',
      'token_namespace',
      'token_text',
      'reference_id',
      'reference_is_external',
      'reference_type',
      'reference_version',
      'number_min',
      'number_max'
    ].each { fieldName ->
      if (fieldName in properties && properties[fieldName]){
        fields += ", "+fieldName
        values += ", ${properties[fieldName]}"
      }
    }
    return GString.EMPTY + " insert into resource_index_term (id, "+fields+") values (nextval('seq_resource_version'), "+values+");"
  }
}
