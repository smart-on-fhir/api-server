package fhir.searchParam

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Interval
import org.joda.time.ReadablePeriod
import org.joda.time.format.ISODateTimeFormat
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import com.mongodb.BasicDBObject;
import com.sun.org.apache.xerces.internal.impl.dv.xs.PrecisionDecimalDV;
import fhir.ResourceIndexDate
import fhir.ResourceIndexString
import fhir.ResourceIndexTerm

public class DateSearchParamHandler extends SearchParamHandler {

	String orderByColumn = "date_min"

	@Override
	protected String paramXpath() {
		return "//"+this.xpath+"/@value";
	}
	
	@Override
	public void processMatchingXpaths(List<Node> nodes, org.w3c.dom.Document r,  List<IndexedValue> index) {

		nodes.each { 
			String val = it.nodeValue
			Interval precision = precisionInterval(val)
			index.add(value([
				date_min: precision.start.toDate(),	
				date_max: precision.end.toDate()
			]))
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

	private java.sql.Date toSqlDate(def d){
		return new java.sql.Date(d.toDate().time)	
	}
	
	@Override
	def joinOn(SearchedValue v) {

		Interval precision = precisionInterval(v.values)
		List fields = []

		if (v.modifier == null){
			fields += [
				name: 'date_min',
				value:  toSqlDate(precision.start),
				operation: '>='
				]
			fields += [
				name: 'date_max',
				value:  toSqlDate(precision.end),
				operation: '<='
				]
		}

		return fields
	}

	@Override
	BasicDBObject searchClause(Map searchedFor){
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}


}