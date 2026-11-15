# LifeAstroAPI Java SDK

Official **Java SDK** for [LifeAstroAPI](https://lifeastroapi.com), a REST
[astrology API](https://lifeastroapi.com/apis/) for Vedic astrology and the Hindu calendar:
panchang, festivals, kundli, dasha, kundli matching, muhurat, transits, horoscopes, numerology,
Lal Kitab, remedies, readings, JSON reports, plus Western astrology and tarot. Location-aware,
in 10 languages, pay per call. Free tier: 1,500 credits a month, no card,
[get a key](https://lifeastroapi.com/signup/).

- **All 321 endpoints** as 322 typed methods in 23 resource groups (`chart().navamsa()` is a
  shortcut for the D9 chart).
- **Java 17+, works in Spring Boot 3.** HTTP through the JDK's `java.net.http.HttpClient`, JSON
  through Jackson (`jackson-databind`, which Spring Boot already ships). No other dependencies.
- **Spring Boot starter**: a shared `LifeAstro` bean from `application.yml`.
- A builder for the parameters of every method, checked when you call `build()`.
- Answers as Jackson `JsonNode`, or mapped to your own classes.
- Blocking methods, and the same methods returning `CompletableFuture` under `client.async()`.
- Typed exceptions, automatic retries (408, 425, 429, 5xx, network) that honour `Retry-After`,
  timeouts, and an `onResponse` hook with your credit balance, rate limit and request id.
- The same methods, names and wire parameters as the [Node.js SDK](https://www.npmjs.com/package/lifeastroapi):
  both are generated from one endpoint manifest.

Docs: **https://lifeastroapi.com/docs/**. Get an API key at **https://lifeastroapi.com/dashboard/keys**.

## Install

Maven:

```xml
<dependency>
  <groupId>com.lifeastroapi</groupId>
  <artifactId>lifeastroapi-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.lifeastroapi:lifeastroapi-java:0.1.0")
```

In a Spring Boot application, use the starter instead (it brings the SDK with it), see
[Spring Boot](#spring-boot):

```xml
<dependency>
  <groupId>com.lifeastroapi</groupId>
  <artifactId>lifeastroapi-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
implementation("com.lifeastroapi:lifeastroapi-spring-boot-starter:0.1.0")
```

## Quick start

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.lifeastroapi.BirthInput;
import com.lifeastroapi.Lang;
import com.lifeastroapi.LifeAstro;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.MilanAshtakootaParams;
import com.lifeastroapi.params.PanchangBasicParams;

LifeAstro client = LifeAstro.builder()
        .apiKey("dv_live_...")          // or leave it out and set LIFEASTRO_API_KEY
        .build();

// Today's panchang in Mumbai, in Gujarati
JsonNode day = client.panchang().basic(PanchangBasicParams.builder()
        .lat(19.076).lon(72.8777).tz("Asia/Kolkata")
        .lang(Lang.GU)
        .build());
System.out.println(day.path("tithi").path("name").asText());       // e.g. સપ્તમી

// A Vedic birth chart
BirthInput arjun = BirthInput.of("1990-01-15", "10:30", "Asia/Kolkata", 19.076, 72.8777);
JsonNode planets = client.chart().planets(ChartPlanetsParams.builder()
        .birth(arjun)
        .build());

// Kundli matching (Ashtakoota, 36 points)
JsonNode match = client.milan().ashtakoota(MilanAshtakootaParams.builder()
        .boy(arjun)
        .girl(BirthInput.of("1992-06-20", "14:15", "Asia/Kolkata", 28.6139, 77.209))
        .lang(Lang.HI)
        .build());
System.out.println(match.path("total").asDouble() + " / 36");
```

The client is immutable and thread-safe. Build **one** and share it across your application; it
keeps its HTTP connections open between calls. It is `AutoCloseable`: close it when the
application stops.

A runnable version is in [examples/quickstart](examples/quickstart/), and a Spring Boot
application with JPA in [examples/spring-boot](examples/spring-boot/).

## Authentication

Your API key (`dv_live_…`) is sent as an `Authorization: Bearer …` header on every request. Give
it to the builder, or set the `LIFEASTRO_API_KEY` environment variable and leave `apiKey(...)`
out. Without a key, `build()` throws an `IllegalStateException` that says so.

```java
LifeAstro.builder().apiKey("dv_live_...").build(); // 1. explicit
LifeAstro.create("dv_live_...");                    // 2. shorthand
LifeAstro.create();                                 // 3. reads LIFEASTRO_API_KEY
```

> **Keep your key on your server.** A `dv_live_…` key carries your account's full billing access.
> Load it from an environment variable or a secrets manager, never from source code, and never
> ship it inside an Android, desktop or browser app, where anyone can read it out. Apps call your
> own backend, which calls LifeAstroAPI with this SDK. (The SDK is for server-side Java; Android
> has no `java.net.http`.) `toString()` of the client shows the key masked (`dv_live_****`).

## Configuration

```java
LifeAstro client = LifeAstro.builder()
        .apiKey("dv_live_...")                    // or LIFEASTRO_API_KEY
        .baseUrl("https://api.lifeastroapi.com")  // override for a local server
        .timeout(Duration.ofSeconds(30))          // per request, to the last byte of the body (default 30 s)
        .maxRetries(2)                            // retries on 408/425/429/5xx/network (default 2)
        .onResponse(info -> { /* credits, rate limit, request id */ })
        .defaultHeader("X-Trace", "abc")          // extra header on every request
        .httpClient(myHttpClient)                 // your own java.net.http.HttpClient (proxy, TLS)
        .objectMapper(myObjectMapper)             // your Jackson mapper, for your DTOs
        .build();
```

## Parameters

Every method takes a parameter object from a builder, named after the method:
`client.panchang().basic(...)` takes `PanchangBasicParams`, `client.western().natal().chart(...)`
takes `WesternNatalChartParams`. All parameter classes are in `com.lifeastroapi.params`.
`build()` throws an `IllegalStateException` listing every required value that is missing.

```java
PanchangBasicParams params = PanchangBasicParams.builder()
        .lat(19.076).lon(72.8777).tz("Asia/Kolkata")   // or .place(Place.of(19.076, 72.8777, "Asia/Kolkata"))
        .date(LocalDate.of(2027, 1, 15))               // or .date("2027-01-15"); defaults to today in tz
        .lang(Lang.GU)
        .build();
```

The SDK sends each value under the wire name the API reads, exactly as the Node.js SDK does:

| Shape | Methods | Builder | Sent as |
|-------|---------|---------|---------|
| Moment | `panchang`, `muhurta` (one date), `prashna` | `lat`, `lon`, `tz`, `date`, `time` or `place(Place)` | `lat`, `lon`, `tz`, `date`, `time`; `date` defaults to today in `tz` |
| Birth chart | `chart`, `dasha`, `lalkitab`, `varshaphal`, narratives, reports | `birth(BirthInput)` or `date`, `time`, `tz`, `lat`, `lon` | unprefixed `date`, `time`, `tz`, `lat`, `lon` |
| Prefixed birth | `milan().mangalDosha()`, `ashtakavarga().sarva()`, ... | `birth(BirthInput)` | `birth.date`, `birth.time`, ... |
| Natal + transit | `transit().tarabala()`, `narrative().transitPhal()`, `western().transits().toNatal()`, ... | `birth(BirthInput)` + `date`, `time`, `tz` | natal chart as `birth.*`, the moment unprefixed |
| Natal + window | `transit().sadeSatiWindows()`, `transit().windows()` | `birth(BirthInput)`, `from`, `tz` | `birth.*` (lat/lon optional: `BirthInput.withoutPlace(...)`), `from`, `tz` |
| Matching | `milan()`, `reports().matchMaking()` | `boy(BirthInput)`, `girl(BirthInput)` | `boy.*`, `girl.*` |
| Western pair | `western().synastry()`, `composite()`, `davison()` | `personA(BirthInput)`, `personB(BirthInput)` | `personA.*`, `personB.*` |
| Rashi | `horoscope()` | `rashi`, `date`, `lang` | `rashi` lowercased |
| Date range | `eclipses()`, `western().ingresses()` | `startDate`, `endDate` | `start_date`, `end_date` |

- `BirthInput.of(date, time, tz, lat, lon)` takes strings and numbers, or `LocalDate`, `LocalTime`,
  `ZoneId` (the time is sent as `HH:MM`, or `HH:MM:SS` when it has seconds). Builders have the same
  `java.time` overloads for their `date`, `time` and `tz` values.
- Vedic methods take `ayanamsa(Ayanamsa.KRISHNAMURTI)` and chart methods
  `houseSystem(HouseSystem.PLACIDUS)`; the plain string value works too.
- Personalised horoscopes (`narrative().horoscopeDailyByLagna()` and the like) take the birth
  chart; `onDate("2026-11-20")` reads another day than today.
- **Data endpoints** (`chart().kundliFacts()`, `chart().yogas()`, `chart().doshas()`,
  `chart().sensitivity()`, `transit().sadeSatiWindows()`, `transit().windows()`,
  `remedies().forGraha()`, `milan().overlay()`, `milan().summary()`) return ids and numbers, no
  prose: planet ids `sun` … `ketu`, `sign_index` 0-based (0 = Aries), `house` 1-based whole-sign
  from the lagna, nakshatra index 0-based, every instant as `*_local` plus `*_jd`.
- A method whose parameters are all optional also has a no-argument form:
  `client.transit().positions()` (today, UTC), `client.meta().version()`.
- Parameter objects are immutable and have value equality (`equals`, `hashCode`), so they can be a
  cache key. `toQuery()` shows the exact query they send.
- One name differs from the Node.js SDK: Node's `dasha.char` (Chara dasha) is `dasha().chara()`,
  because `char` is a Java keyword.

## Languages

Every Vedic method whose response carries names or text takes an optional `lang`. The same fields
come back in that language; ids, dates, numbers and explicit `*_en` / `*_hi` fields do not change.

```java
JsonNode day = client.panchang().basic(PanchangBasicParams.builder()
        .place(Place.of(19.076, 72.8777, "Asia/Kolkata")).date("2027-01-15")
        .lang(Lang.GU)       // tithi, nakshatra, yoga, karana and vara names in Gujarati
        .build());
```

| `Lang` | Code | Language | Coverage |
|--------|------|----------|----------|
| `EN` | `en` | English (default) | complete |
| `HI` | `hi` | Hindi | complete |
| `MR` | `mr` | Marathi | complete |
| `GU` | `gu` | Gujarati | complete |
| `BN` | `bn` | Bengali | complete |
| `TE` | `te` | Telugu | complete |
| `TA` | `ta` | Tamil | complete |
| `KN` | `kn` | Kannada | complete |
| `ML` | `ml` | Malayalam | complete |
| `PA` | `pa` | Punjabi | names and terms; longer readings are still in English |

- **Where it works:** `panchang`, `calendar`, `festivals`, `eclipses`, `planetMoments`, `transit`,
  `milan`, `muhurta`, `chart`, `prashna`, `numerology`, `dasha`, `varshaphal`, `ashtakavarga`,
  `lalkitab`, `horoscope`, `reports`, `narrative` and `remedies`. The SDK sends it as `lang` and
  `locale` (the API reads either). `Lang.fromCode("gu")` turns a code into the enum, and
  `lang("gu")` takes the plain code.
- **No `lang`** (the builder has no such method): Vedic methods that return only times, numbers or
  ids, such as `panchang().rahuKaal()`, `chart().yogas()` and `transit().windows()`.
- **English only:** `western()`, `tarot()`, `geo()` and `meta()`. `western().narrative()` takes
  `lang` for `en`, `hi` and `mr`.
- **Stable keys:** muhurta `factors[]` items carry an English `id` (`Tithi`, `RahuKaal`, …) next to
  the localized `name`, so match on `id`.

## Resource groups

`client.<group>().<method>(params)`, one method per endpoint:

| Group | What it covers |
|-------|----------------|
| `panchang()` | tithi, nakshatra, yoga, karana, vara, sunrise/sunset, rahu kaal, choghadiya, hora, muhurtas, monthly calendar, `basic` / `advanced` |
| `chart()` | planets, ascendant, houses, divisional (D1–D60), dignity, aspects, shadbala, ashtakvarga, avakhada, KP, astro details; data bundles `kundliFacts`, `yogas`, `doshas`, `sensitivity` |
| `dasha()` | Vimshottari (5 levels), Yogini and Chara (`chara`) |
| `milan()` | Ashtakoota (total, full, per koota), mangal / nadi / shani dosha, navamsa and dasha compatibility, `overlay`, `summary` |
| `transit()` | positions, sade sati, ashtakavarga, tarabala, vedha, double transit, small panoti, dated `sadeSatiWindows` and `windows` |
| `horoscope()` | daily, weekly, monthly by rashi |
| `numerology()` | driver, conductor, soul, personality, destiny, full, advanced, challenges, personal periods |
| `muhurta()` | vivah, naamkaran, griha pravesh, vyapar, yatra, best time, chandra / tara bala |
| `varshaphal()` | annual chart, lord, muntha, mudda dasha, harsha bala, tajika yogas |
| `prashna()` | horary answer, chart, arudha, significators |
| `eclipses()` | solar, lunar, all (date range) |
| `calendar()` | Hindu month, ritu, samvatsara, adhik maas, solstices |
| `festivals()` | festivals and vrats by month, on a date, by vrat family |
| `planetMoments()` | retrograde windows, ingress, combustion window, speed |
| `ashtakavarga()` | sarva, bhinna, kaksha, transit score |
| `lalkitab()` | Lal Kitab chart and dasha |
| `remedies()` | by rule id, `forGraha`, mantra, pooja, vrat, daan, rudraksha, yantra, Lal Kitab |
| `narrative()` | authored readings: lagna, planets, yogas, doshas, dasha phal, transit phal, prashna, outlooks, personalised horoscopes |
| `reports()` | JSON reports: kundli (lite, detailed, brihad), match making, mangal dosha, sade sati, varshaphal, numerology, dasha analysis, horoscopes |
| `geo()` | place search, reverse geocoding, time zone |
| `tarot()` | cards, a card, daily card, spreads, yes/no |
| `meta()` | free catalogs: endpoints with their credit cost, ayanamsas, yogas, doshas, locales, version |
| `western()` | tropical astrology: `natal()`, `horoscope()`, `synastry()`, `composite()`, `davison()`, `transits()`, `progressions()`, `solarArc()`, `returns()`, `dignities()`, `profections()`, `zodiacalReleasing()`, `primaryDirections()`, `interpretation()`, `narrative()`, `heliocentric()`, `astrocartography()`, `eclipses()`, `lunar()`, `compatibility()`, plus `firdaria`, `ingresses`, `retrogradeWindow` |

The Javadoc of each method gives its path, its credit cost and the meaning of every parameter.

## Responses

Each method returns the response JSON as a Jackson `JsonNode`:

```java
JsonNode day = client.panchang().basic(params);
String tithi = day.path("tithi").path("name").asText();
```

Pass a class to map it to your own type instead (any class Jackson can read, or `Map.class`):

```java
public record Tithi(int index, String name, String paksha) {}
public record Basic(Tithi tithi, @JsonProperty("sunrise_local") String sunriseLocal) {}

Basic basic = client.panchang().basic(params, Basic.class);
```

Your classes only need the fields you use: the SDK's own mapper ignores unknown properties (as
Spring Boot's does). If you pass your own `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` on,
annotate your classes with `@JsonIgnoreProperties(ignoreUnknown = true)`. A response that does
not map throws a `LifeAstroException` with code `invalid_response`. For generic types, take the
`JsonNode` and convert it: `mapper.convertValue(node, new TypeReference<List<Item>>() {})`.

The API returns each payload as bare JSON. A `{ data, meta }` envelope is unwrapped if a response
ever carries one; `RequestOptions.builder().raw(true)` returns the body exactly as received.

## Async

`client.async()` has the same groups and methods, returning `CompletableFuture`. Retry waits are
scheduled, not slept; cancelling the future aborts the request.

```java
CompletableFuture<JsonNode> day = client.async().panchang().basic(params);
CompletableFuture<Basic> typed = client.async().panchang().basic(params, Basic.class);

day.thenAccept(d -> System.out.println(d.path("tithi").path("name").asText()));
```

A failed call completes the future exceptionally with the same `LifeAstroException` subclasses
as the blocking methods (wrapped in `ExecutionException` / `CompletionException` by `get()` /
`join()`).

## Error handling

Every non-2xx response throws an unchecked exception carrying the API's error `code()`, the HTTP
`status()` and the `requestId()`:

```java
import com.lifeastroapi.errors.*;

try {
    client.chart().planets(params);
} catch (PaymentRequiredException e) {
    // 402: out of credits, or the trial is used up
} catch (RateLimitException e) {
    Duration wait = e.retryAfter();               // from Retry-After, may be null
} catch (LifeAstroException e) {
    log.error("{} {} {} (request {})", e.status(), e.code(), e.getMessage(), e.requestId());
}
```

| Exception | When |
|-----------|------|
| `BadRequestException` | 400: a parameter is missing, malformed or out of range |
| `AuthenticationException` | 401: missing or invalid API key |
| `PaymentRequiredException` | 402: out of credits |
| `PermissionException` | 403: the key may not make this call (plan, IP allow-list) |
| `NotFoundException` | 404: unknown path or id |
| `RateLimitException` | 429: rate limit or daily quota; `retryAfter()` |
| `ServerException` | 5xx |
| `LifeAstroConnectionException` | no HTTP response (network failure; code `aborted` when the thread was interrupted) |
| `LifeAstroTimeoutException` | the call took longer than the timeout (code `timeout`) |

All extend `LifeAstroException` (a `RuntimeException`). 408, 425, 429, 500, 502, 503, 504,
network errors and timeouts are retried first (exponential backoff from 0.5 s up to 8 s with
jitter, or the `Retry-After` wait up to 30 s), up to `maxRetries` times. Other 4xx are not
retried. Failed calls are free.

## Credits, rate limit and request id

Every response carries your credit balance, your plan's rate limit and a request id in its
headers. `onResponse` hands them to you, for every call (builder) or for one call
(`RequestOptions`):

```java
LifeAstro client = LifeAstro.builder()
        .onResponse(info -> {
            if (info.creditsRemaining() != null && info.creditsRemaining() < 5_000) {
                log.warn("LifeAstroAPI credits low: {}", info.creditsRemaining());
            }
        })
        .build();

client.panchang().basic(params, RequestOptions.onResponse(info ->
        log.debug("{} {}", info.requestId(), info.rateLimit())));
```

`ResponseInfo` is a record:

| Field | From header | Meaning |
|---|---|---|
| `status()` | | HTTP status |
| `path()` | | Endpoint path, e.g. `/v1/panchang/basic` |
| `requestId()` | `X-Request-ID` | Quote it to support |
| `creditsRemaining()` | `X-Credits-Remaining` | Balance after the call (`Long`); unchanged after a failed call (failed calls are free). `null` on the free tier, whose allowance is daily/monthly rather than a balance |
| `rateLimit()` | `X-RateLimit-*` | `RateLimit(limit, remaining, resetAt)` per minute for your plan; `resetAt` is an `Instant` |
| `cache()` | `X-Cache` | `HIT` when the API answered from its cache (billed the same) |
| `language()` | `Content-Language` | Language of the answer |

The hook runs once per call with the final response, success or error; not for retried attempts
or network failures. An exception thrown inside it is ignored, so logging can never break a call.
The client-level hook runs first, then the per-call one.

## Per-call options

```java
client.chart().planets(params, RequestOptions.builder()
        .timeout(Duration.ofSeconds(5))   // instead of the client's
        .raw(true)                        // the body exactly as received
        .onResponse(info -> { })
        .build());
```

## Spring Boot

Add `lifeastroapi-spring-boot-starter` (Spring Boot 3.x, Java 17+). It creates one `LifeAstro`
bean from your properties:

```yaml
# application.yml
lifeastroapi:
  api-key: ${LIFEASTRO_API_KEY}      # from the environment or a secrets store, never committed
  # base-url: https://api.lifeastroapi.com
  # timeout: 30s
  # max-retries: 2
```

or in `application.properties`:

```properties
lifeastroapi.api-key=${LIFEASTRO_API_KEY}
lifeastroapi.timeout=30s
```

Then inject it anywhere:

```java
@Service
public class PanchangService {
    private final LifeAstro astro;

    public PanchangService(LifeAstro astro) {
        this.astro = astro;
    }

    public JsonNode today(double lat, double lon, String tz, Lang lang) {
        return astro.panchang().basic(PanchangBasicParams.builder()
                .lat(lat).lon(lon).tz(tz)
                .date(LocalDate.now(ZoneId.of(tz)))
                .lang(lang)
                .build());
    }
}

@RestController
@RequestMapping("/api/panchang")
public class PanchangController {
    private final PanchangService panchang;

    public PanchangController(PanchangService panchang) {
        this.panchang = panchang;
    }

    @GetMapping("/today")
    public JsonNode today(@RequestParam double lat, @RequestParam double lon,
                          @RequestParam(defaultValue = "Asia/Kolkata") String tz,
                          @RequestParam(defaultValue = "en") String lang) {
        return panchang.today(lat, lon, tz, Lang.fromCode(lang));
    }
}
```

- The key comes from `lifeastroapi.api-key`, else the `LIFEASTRO_API_KEY` environment variable.
  Without either, the application fails to start with a message saying so.
- The bean uses the application's Jackson `ObjectMapper`, so the `Class<T>` overloads follow your
  Jackson settings.
- Define your own `LifeAstro` bean and the starter backs off (`@ConditionalOnMissingBean`).
  `lifeastroapi.enabled=false` turns it off.
- To add a hook or a header without replacing the bean, declare a `LifeAstroBuilderCustomizer`:

```java
@Bean
LifeAstroBuilderCustomizer lifeAstroLogging() {
    return builder -> builder.onResponse(info ->
            log.info("LifeAstroAPI {} {}: {} credits left", info.status(), info.path(), info.creditsRemaining()));
}
```

- Spring closes the client when the application stops.

## Hibernate / JPA

The SDK does not touch your database; it calls the API and returns JSON. A JPA application keeps
its own data and turns it into SDK inputs. This is how that looks (the same code runs as a test in
`lifeastroapi-spring-boot-starter`, and as the [Spring Boot example](examples/spring-boot/)):

**1. Store a customer's birth details in your own entity**

```java
@Entity
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private LocalDate birthDate;
    private LocalTime birthTime;
    private String birthTimezone;   // IANA zone, e.g. "Asia/Kolkata"
    private double birthLat;
    private double birthLon;

    /** 2. Map it to the SDK's input. */
    public BirthInput toBirthInput() {
        return BirthInput.of(birthDate, birthTime, ZoneId.of(birthTimezone), birthLat, birthLon);
    }
}
```

```java
@Transactional(readOnly = true)
public JsonNode birthChart(long customerId, Lang lang) {
    Customer c = customers.findById(customerId).orElseThrow();
    return astro.chart().planets(ChartPlanetsParams.builder().birth(c.toBirthInput()).lang(lang).build());
}
```

**3. Cache what is shared**, for example today's panchang per city, with Spring's `@Cacheable`
(and `@EnableCaching` on a configuration class). Parameter objects have value equality, so they
can be the key; give them an explicit `date`, or the key would not change at midnight:

```java
@Cacheable("panchang")
public JsonNode panchang(PanchangBasicParams params) {
    return astro.panchang().basic(params);
}

// caller (another bean, so the cache proxy applies):
service.panchang(PanchangBasicParams.builder()
        .place(Place.of(19.076, 72.8777, "Asia/Kolkata"))
        .date(LocalDate.now(ZoneId.of("Asia/Kolkata")))
        .lang(Lang.GU)
        .build());
```

Use a cache with an expiry (Caffeine, Redis) in production, so old days drop out.

**4. Save a report's JSON** if you need it again: showing it later costs no credits.

```java
@Entity
public class SavedReport {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
    private String kind;             // "kundli-lite"
    private String lang;             // "gu"
    @Lob                             // on PostgreSQL: @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private String json;
    private Instant createdAt;
}

@Transactional
public SavedReport saveKundliReport(long customerId, Lang lang) {
    Customer c = customers.findById(customerId).orElseThrow();
    JsonNode report = astro.reports().kundliLite(ReportsKundliLiteParams.builder()
            .birth(c.toBirthInput()).name(c.getName()).lang(lang).build());
    return reports.save(new SavedReport(c, "kundli-lite", lang.code(), report.toString()));
}
```

Read it back with `objectMapper.readTree(saved.getJson())`.

## Escape hatch

Call any `/v1/*` endpoint directly, for example one newer than this SDK version:

```java
JsonNode t = client.request("/v1/panchang/tithi",
        Map.of("lat", 28.61, "lon", 77.2, "tz", "Asia/Kolkata", "date", "2027-01-15"));
MyType x = client.request("/v1/...", query, MyType.class, options);
CompletableFuture<JsonNode> f = client.async().request("/v1/meta/version");
```

Keys are the API's wire names; values are strings, numbers, booleans or the SDK's enums. Null and
empty values are left out.

## PDF reports

The JSON report endpoints (`client.reports()`) are in this version. Branded PDF rendering of
those reports, as the Node.js SDK does it (`lifeastroapi/pdf`), is planned for a later version.

## Development

The resource and parameter classes are generated from the Node.js SDK's endpoint manifest, so the
two SDKs have the same endpoints, method names and wire parameters. With the Node SDK checked out
next to this repository (`../lifeastroapi-node`, built with `npm run build`):

```bash
node tool/generate.mjs        # params/, resources/, LifeAstroResources, test Samples + manifest
node tool/node-goldens.mjs    # records the Node SDK's request for every method (parity fixture)
./mvnw verify                 # compiles with -Xlint:all -Werror, runs the tests of both modules
```

`NodeParityTest` checks that every method sends exactly the path and query string the Node.js SDK
sends for the same input, blocking and async, with and without `lang`. To call every method
against a running API (one request each; there is no default URL, so it never reaches production
by accident):

```bash
LIFEASTRO_API_KEY=dv_test_... ./mvnw -pl lifeastroapi-java test -Dtest=LiveSweepTest \
    -Dlifeastro.live.baseUrl=http://127.0.0.1:18080
```

Java 17 or later. The Maven Wrapper (`./mvnw`) downloads Maven 3.9.16 on first use.

## License

MIT, see [LICENSE](LICENSE).
