package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeastroapi.errors.LifeAstroException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Live API sweep: calls EVERY method once against a real API and lists each one the API rejects.
 * This checks that the paths and parameter names match what the server accepts; the stub-server
 * tests cannot see that. A port of the Node SDK's {@code scripts/live-sweep.mjs}. Skipped unless a
 * base URL is given; there is no default, so it never reaches production by accident.
 *
 * <pre>
 * LIFEASTRO_API_KEY=dv_test_... ./mvnw -pl lifeastroapi-java test -Dtest=LiveSweepTest \
 *     -Dlifeastro.live.baseUrl=http://127.0.0.1:18080
 * </pre>
 *
 * <p>Spends credits: one request per endpoint. System properties ({@code -D...}):
 * <ul>
 *   <li>{@code lifeastro.live.baseUrl} (required) the API base URL;</li>
 *   <li>{@code lifeastro.live.skip} comma-separated path prefixes to leave out, default
 *       {@code /v1/reports/} (composed reports cost 500-8000 credits each); pass an empty value to
 *       include them;</li>
 *   <li>{@code lifeastro.live.only} comma-separated substrings; only matching paths run;</li>
 *   <li>{@code lifeastro.live.lang} language sent to every method that takes {@code lang};</li>
 *   <li>{@code lifeastro.live.dump} a directory to write each response to;</li>
 *   <li>{@code lifeastro.live.pauseMs} pause between calls, default 450 (per-minute rate limit);</li>
 *   <li>{@code lifeastro.live.allowProduction=true} to allow {@code api.lifeastroapi.com}.</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "lifeastro.live.baseUrl", matches = ".+")
class LiveSweepTest {

    @Test
    void everyMethodAgainstTheLiveApi() throws IOException, InterruptedException {
        String baseUrl = System.getProperty("lifeastro.live.baseUrl");
        String host = URI.create(baseUrl).getHost();
        if (host != null && host.endsWith("lifeastroapi.com") && !Boolean.getBoolean("lifeastro.live.allowProduction")) {
            fail("Refusing to sweep production (" + baseUrl + "); pass -Dlifeastro.live.allowProduction=true to do so.");
        }
        String apiKey = System.getenv("LIFEASTRO_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            fail("LIFEASTRO_API_KEY is required.");
        }
        List<String> skip = split(System.getProperty("lifeastro.live.skip", "/v1/reports/"));
        List<String> only = split(System.getProperty("lifeastro.live.only", ""));
        String langCode = System.getProperty("lifeastro.live.lang", "");
        Lang lang = langCode.isBlank() ? null : Lang.fromCode(langCode);
        String dump = System.getProperty("lifeastro.live.dump", "");
        long pauseMs = Long.getLong("lifeastro.live.pauseMs", 450);
        Path dumpDir = dump.isBlank() ? null : Files.createDirectories(Path.of(dump));
        ObjectMapper json = new ObjectMapper();

        AtomicReference<ResponseInfo> lastInfo = new AtomicReference<>();
        List<String> failures = new ArrayList<>();
        int ran = 0;
        try (LifeAstro client = LifeAstro.builder().apiKey(apiKey).baseUrl(baseUrl).timeout(Duration.ofSeconds(90))
                .maxRetries(0) // a retried failure is still a failure worth seeing
                .onResponse(lastInfo::set).build()) {
            for (Sample s : Samples.all()) {
                if (skip.stream().anyMatch(s.path()::startsWith)) {
                    continue;
                }
                if (!only.isEmpty() && only.stream().noneMatch(s.path()::contains)) {
                    continue;
                }
                ran++;
                try {
                    JsonNode data = s.sync().apply(client, s.hasLang() ? lang : null);
                    if (dumpDir != null) {
                        json.writerWithDefaultPrettyPrinter().writeValue(dumpDir.resolve(s.key() + ".json").toFile(), data);
                    }
                } catch (LifeAstroException e) {
                    failures.add("FAIL " + s.key() + "  (" + s.path() + ")\n     " + e.status() + " " + e.code() + " " + e.getMessage());
                } catch (RuntimeException e) {
                    failures.add("FAIL " + s.key() + "  (" + s.path() + ")\n     " + e);
                }
                if (pauseMs > 0) {
                    Thread.sleep(pauseMs);
                }
            }
        }
        failures.forEach(System.out::println);
        ResponseInfo info = lastInfo.get();
        System.out.println("\nlive sweep: " + ran + " endpoints called, " + failures.size() + " failed"
                + (lang == null ? "" : " (lang=" + lang.code() + ")")
                + (info == null ? "" : "; last response: credits remaining " + info.creditsRemaining()
                        + ", rate limit " + info.rateLimit() + ", request " + info.requestId()));
        assertTrue(ran > 0, "no method matched");
        assertTrue(failures.isEmpty(), failures.size() + " methods failed:\n" + String.join("\n", failures));
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
