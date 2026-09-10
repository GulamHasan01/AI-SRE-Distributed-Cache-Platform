package com.cache.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cacheServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cache Service API")
                        .description("""
                                Phase 1 — Single Node In-Memory Cache.
                                Provides PUT, GET, DELETE, CLEAR, and STATS operations
                                backed by a thread-safe ConcurrentHashMap.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Cache Platform Team")
                                .email("cache-team@platform.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
