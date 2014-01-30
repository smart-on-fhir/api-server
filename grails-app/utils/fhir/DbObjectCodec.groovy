package fhir
import com.mongodb.DBObject
import com.mongodb.util.JSON

class DbObjectCodec  {
  static decode = { str ->
    return (DBObject) JSON.parse(str);
  }
}
