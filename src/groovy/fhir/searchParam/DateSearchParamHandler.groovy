package fhir.searchParam

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.hl7.fhir.instance.model.Resource
//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Interval
import org.joda.time.ReadablePeriod
import org.joda.time.format.ISODateTimeFormat
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import com.sun.org.apache.xerces.internal.impl.dv.xs.PrecisionDecimalDV;
import fhir.ResourceIndexDate
import fhir.ResourceIndexString
import fhir.ResourceIndexTerm

public class DateSearchParamHandler extends SearchParamHandler {

	String orderByColumn = "date_min"

	@Override
	protected String paramXpath() {
		return "//"+this.xpath
	}

	@Override
	public void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r,  List<IndexedValue> index) {

		nodes.each {

			def low=null, high=null;
			
			if (query("./@value", it)){ // A simple dateTime finds a @value directly
				low = query("./@value", it)[0].nodeValue
				high = query("./@value", it)[0].nodeValue
			} else { // a Period finds a start and end value
				def startVals = query("./f:start/@value", it)
				if (startVals) low = startVals[0].nodeValue
				def endVals = query("./f:end/@value", it)
				if (endVals) high = endVals[0].nodeValue
			}
			
			Map m = [:]
			if (low) m.date_min = toSqlDate(precisionInterval(low).start)
      if (high) m.date_max = toSqlDate(precisionInterval(high).end)
      if (m.size()) index.add(value(m))
		}
	}

	public static Interval precisionInterval(String s) {
		DateTime earliest = ISODateTimeFormat
				.dateOptionalTimeParser()
				.parseDateTime(s)
				.withZone(DateTimeZone.UTC);

		DateTime latest = earliest.plus(precisionOf(s));
		return new Interval(earliest, latest);
	}

	private static ReadablePeriod precisionOf(String s) {

		// Determine precision by length of string
		// up to where the time zone (if any) begins
		Pattern t = ~/(.*?T.*?)[Z\\.\\+\\-]/
		Matcher m = t.matcher(s);

		int len;
		if (m.matches()){
			len = m.group(1).length();
		} else {
			len = s.length();
		}

		if (len <= 5){ // "yyyy-".length
			return org.joda.time.Years.ONE;
		} else if (len <= 8) { // "yyyy-mm-".length
			return org.joda.time.Months.ONE;
		} else if (len <= 11) { // "yyyy-mm-ddT"
			return org.joda.time.Days.ONE;
		} else if (len <= 14) { //yyyy-mm-ddThh:"
			return org.joda.time.Hours.ONE;
		} else if (len <= 17) { //yyyy-mm-ddThh:mm:"
			return org.joda.time.Minutes.ONE;
		} else if (len <= 20) { //yyyy-mm-ddT10:hh:mm:ss:"
			return org.joda.time.Seconds.ONE;
		}

		// Assume it's perfectly precise if it's subsecond
		// (FHIR explicitly allows ignoring seconds on dateTimes).
		return org.joda.time.Seconds.ZERO;
	}

	@Override
	public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
		def ret = new ResourceIndexDate()
		ret.search_param = indexedValue.handler.searchParamName
		ret.version_id = versionId
		ret.fhir_id = fhirId
		ret.fhir_type = fhirType
		ret.date_min = indexedValue.dbFields.date_min
		ret.date_max = indexedValue.dbFields.date_max
		return ret
	}

	private java.sql.Timestamp toSqlDate(DateTime d){
        print "coverting $d to ${d.toInstant().millis}"
		return new java.sql.Timestamp(d.toInstant().millis)
	}

	@Override
	def joinOn(SearchedValue v) {
		v.values.split(",").collect { value ->
                        boolean before = false
                        boolean after = false

                        def inequality = (value =~ /(>=|>|<=|<)(.*)/)
                        if (inequality.matches()){
                          String op = inequality[0][1]
                          if (op.startsWith(">")) after = true
                          if (op.startsWith("<")) before = true
                          value = inequality[0][2]
                        }
         
			Interval precision = precisionInterval(value)
			List fields = []

                        if (before == false) {
                                fields += [
                                        name: 'date_min',
                                        value:  toSqlDate(precision.start),
                                        operation: '>='
                                ]
                        }
                        
                        if (after == false) {
                                fields += [
                                        name: 'date_max',
                                        value:  toSqlDate(precision.end),
                                        operation: '<='
                                ]
                        }

			return fields
		}
	}

}
