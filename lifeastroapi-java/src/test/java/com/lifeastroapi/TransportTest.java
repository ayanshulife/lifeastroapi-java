package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeastroapi.StubServer.Reply;
import com.lifeastroapi.params.ChartPlanetParams;
import com.lifeastroapi.params.PanchangTithiParams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** URL, headers, envelope and body handling: the "transport" cases of the Node SDK's client.test.ts. */
class TransportTest {

    private final StubServer stub = new StubServer();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stop() {
        stub.close();
    }

    private static PanchangTithiParams tithi(double lat, double lon, String tz) {
        return PanchangTithiParams.builder().lat(lat).lon(lon).tz(tz).date("2027-01-15").build();
    }

    @Test
    void buildsAnAuthenticatedGetAndUnwrapsData() throws Exception {
        stub.respond(Reply.ok("{\"data\":{\"number\":3},\"meta\":{\"cache\":\"miss\"}}"));
        try (LifeAstro client = stub.client().build()) {
            JsonNode res = client.panchang().tithi(tithi(28.61, 77.2, "Asia/Kolkata"));
            assertEquals(json.readTree("{\"number\":3}"), res);
        }
        StubServer.Recorded call = stub.last();
        assertEquals("GET", call.method());
        assertEquals("/v1/panchang/tithi", call.path());
        assertEquals("28.61", call.query().get("lat"));
        assertEquals("Asia/Kolkata", call.query().get("tz"));
        assertEquals("Bearer dv_live_test", call.header("Authorization"));
        assertEquals("application/json", call.header("Accept"));
    }

    @Test
    void returnsTheFullEnvelopeWithRaw() throws Exception {
        stub.respond(Reply.ok("{\"data\":{\"x\":1},\"meta\":{\"tier\":\"standard\"}}"));
        try (LifeAstro client = stub.client().build()) {
            JsonNode res = client.request("/v1/panchang/tithi", Map.of("lat", 1, "lon", 2, "tz", "UTC"),
                    RequestOptions.builder().raw(true).build());
            assertEquals(json.readTree("{\"data\":{\"x\":1},\"meta\":{\"tier\":\"standard\"}}"), res);
        }
    }

    @Test
    void omitsEmptyParamsAndSortsTheQueryString() {
        try (LifeAstro client = stub.client().build()) {
            client.panchang().tithi(PanchangTithiParams.builder().lat(28.61).lon(77.2).tz("Asia/Kolkata").date("").build());
        }
        assertFalse(stub.last().query().containsKey("date"));
        assertEquals("/v1/panchang/tithi?lat=28.61&lon=77.2&tz=Asia%2FKolkata", stub.last().pathAndQuery());
    }

    @Test
    void keepsALegitimateZero() {
        try (LifeAstro client = stub.client().build()) {
            client.panchang().tithi(tithi(0, 0, "UTC"));
        }
        assertEquals("0", stub.last().query().get("lat"));
        assertEquals("0", stub.last().query().get("lon"));
    }

    @Test
    void urlEncodesAFixedOffsetTimeZone() {
        try (LifeAstro client = stub.client().build()) {
            client.panchang().tithi(tithi(1, 2, "+05:30"));
        }
        assertTrue(stub.last().pathAndQuery().contains("tz=%2B05%3A30"), stub.last().pathAndQuery());
        assertEquals("+05:30", stub.last().query().get("tz"));
    }

    @Test
    void percentEncodesPathParameters() {
        try (LifeAstro client = stub.client().build()) {
            client.chart().planet(ChartPlanetParams.builder().birth(Fixtures.ARJUN).name("a b/c").build());
        }
        assertEquals("/v1/chart/planet/a%20b%2Fc", stub.last().path());
    }

    @Test
    void sendsDefaultHeadersAndTheUserAgent() {
        try (LifeAstro client = stub.client().defaultHeader("X-Trace", "abc").build()) {
            client.panchang().tithi(tithi(1, 2, "UTC"));
        }
        assertEquals("abc", stub.last().header("X-Trace"));
        assertEquals("lifeastroapi-java/" + LifeAstro.VERSION, stub.last().header("User-Agent"));
    }

    @Test
    void defaultHeadersWinOverTheSdkHeaders() {
        try (LifeAstro client = stub.client().defaultHeader("authorization", "Bearer dv_live_other").build()) {
            client.meta().version();
        }
        assertEquals("Bearer dv_live_other", stub.last().header("Authorization"));
    }

    @Test
    void escapeHatchHitsAnArbitraryPathAndUnwrapsData() throws Exception {
        stub.respond(Reply.ok("{\"data\":{\"v\":1}}"));
        try (LifeAstro client = stub.client().build()) {
            assertEquals(json.readTree("{\"v\":1}"), client.request("/v1/anything", Map.of("a", "b")));
            assertEquals(json.readTree("{\"v\":1}"), client.request("v1/no-slash"));
        }
        assertEquals("/v1/anything", stub.calls().get(0).path());
        assertEquals("b", stub.calls().get(0).query().get("a"));
        assertEquals("/v1/no-slash", stub.calls().get(1).path());
    }

    @Test
    void escapeHatchFormatsValuesAsTheNodeSdk() {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("b", true);
        q.put("a", 5.0);
        q.put("c", Lang.GU);
        q.put("d", Ayanamsa.KRISHNAMURTI);
        q.put("e", null);
        q.put("f", "");
        q.put("g", 1e-7);
        q.put("h", "a b+c");
        try (LifeAstro client = stub.client().build()) {
            client.request("/v1/x", q);
        }
        assertEquals("/v1/x?a=5&b=true&c=gu&d=krishnamurti&g=1e-7&h=a+b%2Bc", stub.last().pathAndQuery());
    }

    @Test
    void returnsABareArrayBodyAsIs() throws Exception {
        stub.respond(Reply.ok("[1,2,3]"));
        try (LifeAstro client = stub.client().build()) {
            assertEquals(json.readTree("[1,2,3]"), client.request("/v1/list"));
        }
    }

    @Test
    void returnsANoDataObjectBodyAsIs() throws Exception {
        stub.respond(Reply.ok("{\"foo\":1,\"bar\":2}"));
        try (LifeAstro client = stub.client().build()) {
            assertEquals(json.readTree("{\"foo\":1,\"bar\":2}"), client.request("/v1/raw"));
        }
    }

    @Test
    void returnsANonJsonSuccessBodyAsText() {
        stub.respond(new Reply(200, "plain text".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "text/plain"), 0));
        try (LifeAstro client = stub.client().build()) {
            JsonNode res = client.request("/v1/text");
            assertTrue(res.isTextual());
            assertEquals("plain text", res.asText());
            assertEquals("plain text", client.request("/v1/text", null, String.class));
        }
    }

    @Test
    void anEmptyBodyIsAJsonNullOrNull() {
        stub.respond(new Reply(200, new byte[0], Map.of(), 0));
        try (LifeAstro client = stub.client().build()) {
            assertTrue(client.request("/v1/empty").isNull());
            assertEquals(null, client.request("/v1/empty", null, Map.class));
        }
    }

    @Test
    void readsAGzipBody() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write("{\"tithi\":{\"name\":\"Saptami\"}}".getBytes(StandardCharsets.UTF_8));
        }
        stub.respond(new Reply(200, bytes.toByteArray(), Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"), 0));
        try (LifeAstro client = stub.client().build()) {
            assertEquals("Saptami", client.request("/v1/x").path("tithi").path("name").asText());
        }
        assertEquals("gzip", stub.last().header("Accept-Encoding"));
    }

    /** A user's own DTO. */
    public static final class Tithi {
        public int number;
        public String name;
    }

    @Test
    void mapsTheResponseToYourOwnClass() {
        stub.respond(Reply.ok("{\"number\":7,\"name\":\"Saptami\",\"paksha\":\"Shukla\"}"));
        try (LifeAstro client = stub.client().build()) {
            Tithi t = client.panchang().tithi(tithi(19.076, 72.8777, "Asia/Kolkata"), Tithi.class);
            assertEquals(7, t.number);
            assertEquals("Saptami", t.name);
            Map<?, ?> asMap = client.panchang().tithi(tithi(19.076, 72.8777, "Asia/Kolkata"), Map.class);
            assertEquals("Shukla", asMap.get("paksha"));
        }
    }

    @Test
    void aResponseThatDoesNotMapIsAnInvalidResponseError() {
        stub.respond(Reply.ok("{\"number\":\"not a number\"}").withHeader("X-Request-ID", "req_map"));
        try (LifeAstro client = stub.client().build()) {
            com.lifeastroapi.errors.LifeAstroException e = org.junit.jupiter.api.Assertions.assertThrows(
                    com.lifeastroapi.errors.LifeAstroException.class,
                    () -> client.panchang().tithi(tithi(1, 2, "UTC"), Tithi.class));
            assertEquals("invalid_response", e.code());
            assertEquals(200, e.status());
            assertEquals("req_map", e.requestId());
        }
    }
}
