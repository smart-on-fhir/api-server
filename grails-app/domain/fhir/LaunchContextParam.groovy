package fhir

class LaunchContextParam {

  String param_name;
  String param_value;
  
  
  static belongsTo = [launch_context:LaunchContext]

  static mapping = {
    table 'launch_context_params'
    version false
  }
}
