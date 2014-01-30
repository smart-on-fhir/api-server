package fhir.auth

import com.google.common.cache.CacheBuilder
import groovy.util.logging.Log4j

import com.google.common.cache.Cache

import org.springframework.beans.factory.InitializingBean

@Log4j
class TokenCache implements InitializingBean {

  String spec;
  Cache cache;

  void setSpecification(String inSpec) {
    spec = inSpec
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    log.debug("Initialized token cache")
    cache = CacheBuilder
        .from(spec)
        .build();
  }
}
