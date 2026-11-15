package com.lifeastroapi;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.util.Optional;

/**
 * What the API reported about one call besides its body: the request id, your credit balance
 * after the call, your plan's per-minute rate limit and whether the answer came from the API's
 * cache. Passed to {@code onResponse} (client-level or per call) once for every final HTTP
 * response, success or error; never for attempts that were retried, nor for network failures.
 *
 * @param status           HTTP status of the response
 * @param path             request path, e.g. {@code /v1/panchang/basic}
 * @param requestId        {@code X-Request-ID}; quote it to support. Null when absent
 * @param creditsRemaining {@code X-Credits-Remaining}: the balance after this call (unchanged
 *                         after a failed call, which is free). Null on free-tier accounts, whose
 *                         allowance is not a balance, and on calls that cost nothing
 * @param rateLimit        {@code X-RateLimit-*}: requests per minute, how many are left and when
 *                         the window resets. Null unless all three headers are present
 * @param cache            {@code X-Cache}, e.g. {@code HIT} when the API answered from its cache
 *                         (billed the same). Null when absent
 * @param language         {@code Content-Language}: the language of the answer. Null when absent
 */
public record ResponseInfo(
        int status,
        String path,
        String requestId,
        Long creditsRemaining,
        RateLimit rateLimit,
        String cache,
        String language) {

    static ResponseInfo from(int status, String path, HttpHeaders headers) {
        Long limit = number(headers, "x-ratelimit-limit");
        Long remaining = number(headers, "x-ratelimit-remaining");
        Long reset = number(headers, "x-ratelimit-reset");
        RateLimit rateLimit = limit != null && remaining != null && reset != null
                ? new RateLimit(limit, remaining, Instant.ofEpochSecond(reset))
                : null;
        return new ResponseInfo(
                status,
                path,
                text(headers, "x-request-id"),
                number(headers, "x-credits-remaining"),
                rateLimit,
                text(headers, "x-cache"),
                text(headers, "content-language"));
    }

    private static String text(HttpHeaders headers, String name) {
        Optional<String> v = headers.firstValue(name);
        return v.isPresent() && !v.get().isEmpty() ? v.get() : null;
    }

    private static Long number(HttpHeaders headers, String name) {
        String v = text(headers, name);
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(s);
                return Double.isFinite(d) ? (long) Math.floor(d) : null;
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }
}
