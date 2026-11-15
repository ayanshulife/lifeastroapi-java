package com.lifeastroapi;

import java.time.Instant;

/**
 * Your plan's per-minute rate limit, from the {@code X-RateLimit-*} response headers.
 *
 * @param limit     requests allowed per minute ({@code X-RateLimit-Limit})
 * @param remaining requests left in the current window ({@code X-RateLimit-Remaining})
 * @param resetAt   when the window resets ({@code X-RateLimit-Reset}, Unix seconds)
 */
public record RateLimit(long limit, long remaining, Instant resetAt) {
}
