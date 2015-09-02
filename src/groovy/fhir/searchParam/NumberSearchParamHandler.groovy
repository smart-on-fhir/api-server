package fhir.searchParam

import org.hl7.fhir.instance.model.Resource
//import org.hl7.fhir.instance.model.Conformance.SearchParamType
import org.w3c.dom.Node

import fhir.ResourceIndexNumber
import fhir.ResourceIndexTerm

public class NumberSearchParamHandler extends SearchParamHandler {

    String orderByField = "number_min"

    @Override
    protected String paramXpath() {
        return "//"+this.xpath;
    }

    @Override
    public void processMatchingXpaths(List<Node> numberNodes, org.w3c.dom.Document r, List<IndexedValue> index) {

        for (Node n : numberNodes) {

            // plain numbers
            query("./@value", n).each { plainNumber->
                index.add(value([
                    number_min: plainNumber,
                    number_max: plainNumber
                ]))
            }

            // numbers in Quantity or one of its subtypes
            query("./f:value", n).each { q ->
                def number = query("./@value", q)
                def comparator = query("../f:comparator/@value", q)

                def number_min = null;
                def number_max = null;

                if (!comparator) {
                    number_min = number
                    number_max = number
                } else if (comparator == '<' || comparator == '<=') {
                    number_max = number;
                } else if (comparator == '>' || comparator == '>=') {
                    number_min = number;
                }

                index.add(value([
                    number_min: number_min,
                    number_max: number_max
                ]))
            }
        }
    }

    @Override
    public ResourceIndexTerm createIndex(IndexedValue indexedValue, versionId, fhirId, fhirType) {
        def ret = new ResourceIndexNumber()
        ret.search_param = indexedValue.handler.searchParamName
        ret.version_id = versionId
        ret.fhir_id = fhirId
        ret.fhir_type = fhirType
        ret.number_min = indexedValue.dbFields.number_min
        ret.number_max = indexedValue.dbFields.number_max
        return ret
    }

}

