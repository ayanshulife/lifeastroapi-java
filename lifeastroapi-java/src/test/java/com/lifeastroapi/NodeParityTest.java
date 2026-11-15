package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * For every method, the Java SDK sends the same path and query string as the Node.js SDK with the
 * same inputs, blocking and async, without and with {@code lang=bn}. The Node side is recorded by
 * {@code tool/node-goldens.mjs} into {@code node-requests.json} (rerun it after
 * {@code node tool/generate.mjs}).
 */
class NodeParityTest {

    private static StubServer stub;
    private static LifeAstro client;
    private static JsonNode golden;

    @BeforeAll
    static void start() throws IOException {
        stub = new StubServer();
        client = stub.client().build();
        try (InputStream in = NodeParityTest.class.getResourceAsStream("node-requests.json")) {
            assertNotNull(in, "node-requests.json missing; run `node tool/node-goldens.mjs`");
            golden = new ObjectMapper().readTree(in).path("methods");
        }
    }

    @AfterAll
    static void stop() {
        client.close();
        stub.close();
    }

    @Test
    void goldensCoverEveryMethod() {
        List<String> keys = new ArrayList<>();
        golden.fieldNames().forEachRemaining(keys::add);
        assertEquals(Samples.all().stream().map(Sample::key).sorted().toList(), keys.stream().sorted().toList(),
                "rerun `node tool/node-goldens.mjs` after `node tool/generate.mjs`");
    }

    @TestFactory
    Stream<DynamicTest> sendsTheSameRequestAsNode() {
        return Samples.all().stream().map(s -> DynamicTest.dynamicTest(s.key(), () -> check(s)));
    }

    private static void check(Sample s) throws Exception {
        JsonNode g = golden.path(s.key());
        assertFalse(g.isMissingNode(), "no Node golden for " + s.key());
        String url = g.path("url").asText();
        assertEquals(normalize(s, url), normalize(s, sent(() -> s.sync().apply(client, null))), "blocking");
        assertEquals(normalize(s, url), normalize(s, sent(() -> s.async().apply(client.async(), null).get(10, TimeUnit.SECONDS))), "async");
        if (s.hasLang()) {
            String langUrl = g.path("lang_url").asText(null);
            assertNotNull(langUrl, "Node sent no lang for " + s.key());
            assertEquals(normalize(s, langUrl), normalize(s, sent(() -> s.sync().apply(client, Lang.BN))), "blocking, lang");
            assertEquals(normalize(s, langUrl), normalize(s, sent(() -> s.async().apply(client.async(), Lang.BN).get(10, TimeUnit.SECONDS))), "async, lang");
        } else {
            assertFalse(g.has("lang_url"), "Node sends lang for " + s.key() + " but Java has no lang parameter");
        }
    }

    @FunctionalInterface
    interface Call {
        Object run() throws Exception;
    }

    private static synchronized String sent(Call call) throws Exception {
        stub.reset();
        call.run();
        return stub.last().pathAndQuery();
    }

    /** Location-less moments default to today's date, which differs between recording and today. */
    private static String normalize(Sample s, String url) {
        if (!s.shape().equals("instant")) {
            return url;
        }
        return url.replaceAll("([?&])date=\\d{4}-\\d{2}-\\d{2}", "$1date=TODAY");
    }
}
