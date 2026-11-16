package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The client has one method per entry of the Node SDK's manifest (323), reachable as
 * {@code client.<group>().<method>(params)} on the blocking and the async side.
 */
class ManifestTest {

    private static JsonNode manifest;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = ManifestTest.class.getResourceAsStream("manifest.json")) {
            assertNotNull(in, "manifest.json missing; run `node tool/generate.mjs`");
            manifest = new ObjectMapper().readTree(in);
        }
    }

    @Test
    void oneMethodPerNodeManifestEntry() {
        assertEquals(323, manifest.path("node_entries").asInt(), "Node SDK manifest size");
        assertEquals(manifest.path("node_entries").asInt(), manifest.path("methods").size());
        assertEquals(manifest.path("methods").size(), Samples.all().size());
    }

    @Test
    void everyMethodIsReachableBlockingAndAsync() throws Exception {
        try (LifeAstro client = LifeAstro.create("dv_live_test")) {
            for (JsonNode m : manifest.path("methods")) {
                String key = m.path("key").asText();
                String[] parts = key.split("\\.");
                Object sync = client;
                Object async = client.async();
                for (int i = 0; i < parts.length - 1; i++) {
                    sync = sync.getClass().getMethod(parts[i]).invoke(sync);
                    async = async.getClass().getMethod(parts[i]).invoke(async);
                }
                String name = parts[parts.length - 1];
                String params = m.path("params").isNull() ? null : "com.lifeastroapi.params." + m.path("params").asText();
                Class<?>[] args = params == null ? new Class<?>[0] : new Class<?>[] {Class.forName(params)};
                Method s = sync.getClass().getMethod(name, args);
                Method a = async.getClass().getMethod(name, args);
                assertEquals(JsonNode.class, s.getReturnType(), key);
                assertEquals(CompletableFuture.class, a.getReturnType(), key);
            }
        }
    }

    @Test
    void generatedResourceClassesHaveExactly323DistinctMethods() throws Exception {
        Set<String> names = new HashSet<>();
        for (String cls : resourceClasses()) {
            Class<?> c = Class.forName("com.lifeastroapi.resources." + cls);
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.getReturnType().getPackageName().equals("com.lifeastroapi.resources")) {
                    continue; // sub-group accessors
                }
                names.add(cls + "." + m.getName());
            }
        }
        assertEquals(323, names.size());
    }

    private static List<String> resourceClasses() {
        List<String> out = new ArrayList<>();
        for (Method m : LifeAstroResources.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            Class<?> group = m.getReturnType();
            out.add(group.getSimpleName());
            for (Method sub : group.getDeclaredMethods()) {
                if (sub.getParameterCount() == 0 && sub.getReturnType().getPackageName().equals("com.lifeastroapi.resources")) {
                    out.add(sub.getReturnType().getSimpleName());
                }
            }
        }
        assertTrue(out.size() > 40, out.toString());
        return out;
    }

    @Test
    void javaKeywordMethodIsRenamed() throws Exception {
        assertNotNull(com.lifeastroapi.resources.Dasha.class.getMethod("chara", com.lifeastroapi.params.DashaCharaParams.class));
        boolean found = false;
        for (JsonNode m : manifest.path("methods")) {
            if (m.path("key").asText().equals("dasha.chara")) {
                assertEquals("dasha.char", m.path("nodeKey").asText());
                found = true;
            }
        }
        assertTrue(found);
    }
}
