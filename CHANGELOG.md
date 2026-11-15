# Changelog

## 0.1.0 (2026-11-15)

First release of the official Java SDK.

- `com.lifeastroapi:lifeastroapi-java`: the `LifeAstro` client for Java 17 and later, built with
  `LifeAstro.builder().apiKey(...).baseUrl(...).timeout(...).maxRetries(...).onResponse(...).build()`.
  The API key falls back to the `LIFEASTRO_API_KEY` environment variable. Immutable, thread-safe
  and `AutoCloseable`.
- 322 methods in 23 resource groups (`client.panchang().basic(...)`, `client.chart().planets(...)`,
  `client.western().natal().chart(...)`, ...): one method per endpoint, generated from the same
  manifest as the Node.js SDK 0.10.0, with the Node method names. One exception: Node's
  `dasha.char` is `dasha().chara()` (`char` is a Java keyword).
- A parameter class with a builder per method (`PanchangBasicParams.builder()...build()`), with
  the required values checked in `build()`, `java.time` overloads, `BirthInput` and `Place`
  value types, and the Node SDK's wire names: unprefixed `date` / `time` / `tz` / `lat` / `lon` for
  a chart, `birth.*` for the natal chart of transit-style methods, `boy.*` / `girl.*` for kundli
  matching, `personA.*` / `personB.*` for Western pairs. Moment methods send today's date in the
  given `tz` when `date` is left out. Parameter objects have value equality.
- `lang` on every Vedic method whose response carries names or text, sent as `lang` and `locale`;
  `enum Lang { EN, HI, MR, GU, BN, TE, TA, KN, ML, PA }`. `Ayanamsa` and `HouseSystem` enums.
- Responses as Jackson `JsonNode`, or mapped to your class with the `Class<T>` overload of every
  method. `{ data, meta }` envelopes are unwrapped; `raw(true)` returns the body as received.
- `client.async()`: the same methods returning `CompletableFuture`, with scheduled (non-blocking)
  retry waits and cancellation.
- Unchecked exceptions with `status()`, `code()`, `requestId()` and `body()`:
  `BadRequestException`, `AuthenticationException`, `PaymentRequiredException`,
  `PermissionException`, `NotFoundException`, `RateLimitException` (`retryAfter()`),
  `ServerException`, `LifeAstroConnectionException` and `LifeAstroTimeoutException`, all extending
  `LifeAstroException`.
- Automatic retries with exponential backoff on 408, 425, 429, 500, 502, 503, 504, network errors
  and timeouts, honouring `Retry-After`; per-call `RequestOptions` (timeout, raw body, hook).
- `onResponse` hook (client-level and per call) with a `ResponseInfo` record: status, path,
  request id, credits remaining, rate limit (`RateLimit` with `resetAt` as an `Instant`), cache
  and language. Same behaviour as the Node.js SDK 0.10.0.
- `client.request(path, query, ...)` to call any `/v1/*` endpoint directly.
- Dependencies: the JDK's `java.net.http.HttpClient` and `jackson-databind` (2.21.4, as Spring
  Boot 3.5 manages it). gzip responses are decoded.
- `com.lifeastroapi:lifeastroapi-spring-boot-starter`: Spring Boot 3 auto-configuration of a
  `LifeAstro` bean from `lifeastroapi.api-key`, `lifeastroapi.base-url`, `lifeastroapi.timeout` and
  `lifeastroapi.max-retries` (with IDE metadata); backs off for your own bean, uses the
  application's `ObjectMapper`, applies `LifeAstroBuilderCustomizer` beans,
  `lifeastroapi.enabled=false` to turn it off.
- README: plain Java, Spring Boot and Hibernate / JPA usage. Not in this version: the PDF
  rendering of the Node.js SDK (the JSON report endpoints are included).
