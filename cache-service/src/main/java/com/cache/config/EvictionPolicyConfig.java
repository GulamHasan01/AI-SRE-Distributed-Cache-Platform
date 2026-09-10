package com.cache.config;

import com.cache.eviction.EvictionPolicy;
import com.cache.eviction.LRUEvictionPolicy;
import com.cache.eviction.NoEvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvictionPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(EvictionPolicyConfig.class);

    @Bean
    public EvictionPolicy evictionPolicy(CacheProperties properties) {
        EvictionPolicy policy = switch (properties.getEvictionPolicy()) {
            case LRU -> new LRUEvictionPolicy();
            case NO_EVICTION -> new NoEvictionPolicy();
        };

        log.info("Eviction policy configured: {}", policy.policyName());
        return policy;
    }
}
