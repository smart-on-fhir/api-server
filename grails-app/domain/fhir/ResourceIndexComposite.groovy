package fhir

class ResourceIndexComposite extends ResourceIndex {
	String composite_value


    static mapping = {
        tablePerHierarchy false
		version false
    }
}

