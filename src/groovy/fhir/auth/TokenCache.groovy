package fhir.auth

import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache

import org.springframework.beans.factory.InitializingBean

class TokenCache implements InitializingBean {

	String spec;
	Cache cache;
	
	void setSpecification(String inSpec) {
		spec = inSpec
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		println("Crazy initted token cache!")
		cache = CacheBuilder
				.from(spec)
				.build();		
	}
	
}
