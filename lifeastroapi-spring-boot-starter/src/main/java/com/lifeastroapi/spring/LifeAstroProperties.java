package com.lifeastroapi.spring;

import com.lifeastroapi.LifeAstro;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the {@link LifeAstro} bean, under {@code lifeastroapi.*} in
 * {@code application.properties} or {@code application.yml}:
 *
 * <pre>
 * lifeastroapi.api-key=${LIFEASTRO_API_KEY}
 * lifeastroapi.base-url=https://api.lifeastroapi.com
 * lifeastroapi.timeout=30s
 * lifeastroapi.max-retries=2
 * </pre>
 */
@ConfigurationProperties(prefix = "lifeastroapi")
public class LifeAstroProperties {

    /** Whether to create the LifeAstro client bean. */
    private boolean enabled = true;

    /**
     * API key (dv_live_...). When empty, the LIFEASTRO_API_KEY environment variable is used. Keep it
     * out of source control: point it at an environment variable or a secrets store.
     */
    private String apiKey;

    /** API base URL. Override it for a local server. */
    private String baseUrl = LifeAstro.DEFAULT_BASE_URL;

    /** Per-request timeout, from sending the request to the last byte of the body. */
    private Duration timeout = LifeAstro.DEFAULT_TIMEOUT;

    /** Automatic retries on 408, 425, 429, 5xx and network errors (exponential backoff, Retry-After). */
    private int maxRetries = LifeAstro.DEFAULT_MAX_RETRIES;

    /**
     * Whether the bean is created.
     *
     * @return true unless {@code lifeastroapi.enabled=false}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the bean is created.
     *
     * @param enabled false to create no bean
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The API key.
     *
     * @return the key, or null
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Sets the API key.
     *
     * @param apiKey the key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * The API base URL.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the API base URL.
     *
     * @param baseUrl an http or https URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * The per-request timeout.
     *
     * @return the timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Sets the per-request timeout ({@code 30s}, {@code 1m}, {@code 5000ms}).
     *
     * @param timeout the timeout
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * The number of automatic retries.
     *
     * @return the retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the number of automatic retries.
     *
     * @param maxRetries the retry count; 0 for none
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /** Never shows the key. */
    @Override
    public String toString() {
        return "LifeAstroProperties{enabled=" + enabled + ", apiKey=" + (apiKey == null || apiKey.isEmpty() ? "unset" : "****")
                + ", baseUrl=" + baseUrl + ", timeout=" + timeout + ", maxRetries=" + maxRetries + "}";
    }
}
