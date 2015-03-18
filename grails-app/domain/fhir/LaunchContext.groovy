package fhir

import grails.converters.JSON
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date;
import org.codehaus.groovy.grails.web.json.JSONObject

class LaunchContext {

  static hasMany = [params: LaunchContextParam]

  String created_by
  String username
  Date created_at = new Date()
  String client_id
  
  public static TimeZone tz;
  public static DateFormat df;
  
  static {
    tz = TimeZone.getTimeZone("UTC");
    df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
  }
  
  static mapping = {
    table 'launch_context'
    id column: 'launch_id'
    version false
  }

  public JSON asJson(){
    JSONObject j = new JSONObject();
    j.put("launch_id", id.toString());
    j.put("username", username);
    j.put("created_by", created_by);
    j.put("created_at", df.format(created_at));

    JSONObject ps = new JSONObject();
    params.each {
      ps.put(it.param_name, it.param_value)
    }
    
    ps.put("need_patient_banner", true)
    ps.put("smart_style_url", "https://fhir.smarthealthit.org/stylesheets/smart_v1.json")

    j.put("parameters", ps);
    
    def ret = j as JSON
    ret.setPrettyPrint(true)
    return ret
  }
}
