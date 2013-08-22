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

public class DateSearchParamHandler extends SearchParamHandler {

	@Override
	protected String paramXpath() {
		return "//"+this.xpath+"/@value";
	}
	
	@Override
	public void processMatchingXpaths(List<Node> nodes, List<SearchParamValue> index) {

		setMissing(nodes.size() == 0, index);
		
		nodes.each { 
			String val = it.nodeValue
			Interval precision = precisionInterval(val)
			index.add(value(":before",precision.start.toDate()));
			index.add(value(":after",precision.end.toDate()));
		}
	}

	private static Interval precisionInterval(String s) {
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
	BasicDBObject searchClause(Map searchedFor){
		
		Interval precision = precisionInterval(searchedFor.value)
		
		if (searchedFor.modifier == null){
			return and(match(
					k: fieldName+':before',
					v: [
						$gte: precision.start.toDate() 
					]),match(
					k: fieldName+':after',
					v: [
						$lte: precision.end.toDate()
					]
			))
		}
		
		if (searchedFor.modifier == "before"){
			return match(
				k: fieldName+':before',
				v: [$lte: precision.end.toDate()]
			)
		}
		
		if (searchedFor.modifier == "after"){
			return match(
				k: fieldName+':after',
				v: [$gte: precision.start.toDate()]
			)
		}
		
		throw new RuntimeException("Unknown modifier: " + searchedFor)
	}


}