package com.smart_logistics.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Jackson 2 mapper used by the Amap snapshot persistence layer.
 * Spring Boot 4 auto-configures Jackson 3 for HTTP serialization, while the
 * existing provider parsers intentionally still use Jackson 2.
 */
@Configuration
public class LegacyJacksonConfig {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
