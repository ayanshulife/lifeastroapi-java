package com.lifeastroapi.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeastroapi.LifeAstro;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Creates one shared {@link LifeAstro} client bean from the {@code lifeastroapi.*} properties
 * ({@link LifeAstroProperties}). Inject it anywhere; the client is thread-safe.
 *
 * <ul>
 *   <li>Backs off when the application defines its own {@code LifeAstro} bean.</li>
 *   <li>Uses the application's Jackson {@link ObjectMapper} (when there is exactly one) to map
 *       responses to your classes.</li>
 *   <li>Applies every {@link LifeAstroBuilderCustomizer} bean, in order.</li>
 *   <li>Turned off with {@code lifeastroapi.enabled=false}.</li>
 * </ul>
 *
 * <p>Without an API key (neither {@code lifeastroapi.api-key} nor the {@code LIFEASTRO_API_KEY}
 * environment variable) the application fails to start with a message saying so.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(LifeAstro.class)
@ConditionalOnProperty(prefix = "lifeastroapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LifeAstroProperties.class)
public class LifeAstroAutoConfiguration {

    /** Creates the auto-configuration. */
    public LifeAstroAutoConfiguration() {
    }

    /**
     * The shared client. Spring closes it when the context shuts down.
     *
     * @param properties   the {@code lifeastroapi.*} settings
     * @param objectMapper the application's mapper, if it has exactly one
     * @param customizers  the builder customizers
     * @return the client
     */
    @Bean
    @ConditionalOnMissingBean
    public LifeAstro lifeAstro(LifeAstroProperties properties, ObjectProvider<ObjectMapper> objectMapper,
            ObjectProvider<LifeAstroBuilderCustomizer> customizers) {
        LifeAstro.Builder builder = LifeAstro.builder();
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.apiKey(properties.getApiKey());
        }
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        if (properties.getTimeout() != null) {
            builder.timeout(properties.getTimeout());
        }
        builder.maxRetries(properties.getMaxRetries());
        ObjectMapper mapper = objectMapper.getIfUnique();
        if (mapper != null) {
            builder.objectMapper(mapper);
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }
}
