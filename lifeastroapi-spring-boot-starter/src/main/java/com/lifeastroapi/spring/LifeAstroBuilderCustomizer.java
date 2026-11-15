package com.lifeastroapi.spring;

import com.lifeastroapi.LifeAstro;

/**
 * Adjusts the {@link LifeAstro.Builder} before the auto-configured bean is built, after the
 * {@code lifeastroapi.*} properties are applied. Declare one or more as beans, for example to log
 * credits with {@code onResponse} or to add a header:
 *
 * <pre>{@code
 * @Bean
 * LifeAstroBuilderCustomizer creditsLog() {
 *     return builder -> builder.onResponse(info -> log.info("credits left: {}", info.creditsRemaining()));
 * }
 * }</pre>
 *
 * <p>Customizers run in {@link org.springframework.core.annotation.Order} order.
 */
@FunctionalInterface
public interface LifeAstroBuilderCustomizer {

    /**
     * Customizes the builder.
     *
     * @param builder the builder of the bean
     */
    void customize(LifeAstro.Builder builder);
}
