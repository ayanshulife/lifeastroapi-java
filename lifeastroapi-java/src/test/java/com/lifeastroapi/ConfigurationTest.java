package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "configuration" cases of the Node SDK's client.test.ts, plus Java specifics. */
class ConfigurationTest {

    private final StubServer stub = new StubServer();

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void throwsWhenNoApiKeyIsProvided() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> LifeAstro.builder().env(name -> null).build());
        assertTrue(e.getMessage().contains("API key"), e.getMessage());
        assertTrue(e.getMessage().contains("LIFEASTRO_API_KEY"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> LifeAstro.builder().apiKey("  ").env(name -> "").build());
    }

    @Test
    void readsTheApiKeyFromTheEnvironment() {
        Map<String, String> env = Map.of("LIFEASTRO_API_KEY", "dv_live_env");
        try (LifeAstro client = LifeAstro.builder().env(env::get).baseUrl(stub.baseUrl()).maxRetries(0).build()) {
            client.meta().version();
        }
        assertEquals("Bearer dv_live_env", stub.last().header("Authorization"));
    }

    @Test
    void anExplicitKeyWinsOverTheEnvironment() {
        try (LifeAstro client = LifeAstro.builder().apiKey("dv_live_explicit").env(n -> "dv_live_env")
                .baseUrl(stub.baseUrl()).build()) {
            client.meta().version();
        }
        assertEquals("Bearer dv_live_explicit", stub.last().header("Authorization"));
    }

    @Test
    void createWithAKey() {
        try (LifeAstro client = LifeAstro.create("dv_live_str")) {
            assertEquals(LifeAstro.DEFAULT_BASE_URL, client.baseUrl());
            assertEquals(Duration.ofSeconds(30), client.timeout());
            assertEquals(2, client.maxRetries());
        }
    }

    @Test
    void neverPrintsTheKey() {
        try (LifeAstro client = LifeAstro.create("dv_live_abcdef123456")) {
            String s = client.toString();
            assertFalse(s.contains("abcdef123456"), s);
            assertTrue(s.contains("dv_live_****"), s);
            assertFalse(client.async().toString().contains("abcdef"));
        }
    }

    @Test
    void normalisesAndChecksTheBaseUrl() {
        try (LifeAstro client = LifeAstro.builder().apiKey("dv_live_x").baseUrl(stub.baseUrl() + "///").build()) {
            assertEquals(stub.baseUrl(), client.baseUrl());
            client.meta().version();
        }
        assertEquals("/v1/meta/version", stub.last().path());
        assertThrows(IllegalArgumentException.class, () -> LifeAstro.builder().apiKey("k").baseUrl("ftp://x").build());
        assertThrows(IllegalArgumentException.class, () -> LifeAstro.builder().apiKey("k").baseUrl("not a url").build());
        assertThrows(IllegalArgumentException.class, () -> LifeAstro.builder().timeout(Duration.ZERO));
    }

    @Test
    void usesAnInjectedHttpClientAndLeavesItOpen() {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        try (LifeAstro client = stub.client().httpClient(http).build()) {
            client.meta().version();
        }
        // Still usable after the LifeAstro client is closed.
        try (LifeAstro again = stub.client().httpClient(http).build()) {
            again.meta().version();
        }
        assertEquals(2, stub.calls().size());
    }

    @Test
    void aClosedClientRefusesCalls() {
        LifeAstro client = stub.client().build();
        client.close();
        assertThrows(IllegalStateException.class, () -> client.meta().version());
        assertThrows(IllegalStateException.class, () -> client.async().meta().version());
    }

    @Test
    void versionMatchesThePom() {
        String pom = System.getProperty("lifeastro.projectVersion");
        if (pom != null) {
            assertEquals(pom, LifeAstro.VERSION);
        }
    }
}
