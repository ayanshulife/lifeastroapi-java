package com.lifeastroapi.params;

import com.lifeastroapi.BirthInput;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helpers of the generated parameter classes. The wire names they write ({@code birth.lat},
 * {@code boy.date}, {@code start_date}, ...) are the contract with the API and match
 * {@code src/params.ts} of the Node.js SDK.
 */
final class Wire {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Wire() {
    }

    /** Sets {@code key} only when {@code value} is meaningful: null and "" are skipped; 0 and false are kept. */
    static void put(Map<String, Object> query, String key, Object value) {
        if (value == null || "".equals(value)) {
            return;
        }
        query.put(key, value);
    }

    /** Copies a birth onto prefixed wire names: {@code boy.date}, {@code boy.time}, ... */
    static void putBirth(Map<String, Object> query, String prefix, BirthInput birth) {
        if (birth == null) {
            return;
        }
        put(query, prefix + "lat", birth.lat());
        put(query, prefix + "lon", birth.lon());
        put(query, prefix + "date", birth.date());
        put(query, prefix + "time", birth.time());
        put(query, prefix + "tz", birth.tz());
    }

    /** The birth place as unprefixed {@code lat} / {@code lon}. */
    static void putPlace(Map<String, Object> query, BirthInput birth) {
        if (birth == null) {
            return;
        }
        put(query, "lat", birth.lat());
        put(query, "lon", birth.lon());
    }

    /**
     * Today's date ({@code YYYY-MM-DD}) in {@code tz}. The API has no "today" default: every moment
     * endpoint rejects a request without {@code date}, so the SDK sends one. Falls back to the UTC
     * date when {@code tz} is not a zone Java knows (an IANA name or an offset such as
     * {@code +05:30}).
     */
    static String todayIn(String tz) {
        return todayIn(tz, Instant.now());
    }

    static String todayIn(String tz, Instant now) {
        ZoneId zone = ZoneOffset.UTC;
        if (tz != null && !tz.isBlank()) {
            try {
                zone = ZoneId.of(tz.trim());
            } catch (DateTimeException e) {
                // Unknown zone name, or a form such as "5.5" that java.time does not read.
            }
        }
        return LocalDate.ofInstant(now, zone).toString();
    }

    /**
     * Fills the {@code {name}} placeholders of a path template, percent-encoding each value as
     * JavaScript's {@code encodeURIComponent} does.
     */
    static String path(String template, String... namesAndValues) {
        String path = template;
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String name = namesAndValues[i];
            String value = namesAndValues[i + 1];
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Missing required path parameter \"" + name + "\" for " + template);
            }
            path = path.replace("{" + name + "}", encodeUriComponent(value));
        }
        return path;
    }

    /** JavaScript's {@code encodeURIComponent}. */
    static String encodeUriComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }

    static String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static String time(LocalTime time) {
        if (time == null) {
            return null;
        }
        return (time.getSecond() == 0 && time.getNano() == 0 ? HH_MM : HH_MM_SS).format(time);
    }

    static String zone(ZoneId zone) {
        return zone == null ? null : zone.getId();
    }

    static BirthInput requireBirth(BirthInput birth, String name) {
        return Objects.requireNonNull(birth, name);
    }

    /** Adds {@code name} to {@code missing} when {@code value} is null (or an empty path value). */
    static void require(List<String> missing, String name, Object value) {
        if (value == null) {
            missing.add(name);
        }
    }

    /** Like {@link #require}, also for a birth object without coordinates. */
    static void requirePlace(List<String> missing, String name, BirthInput birth) {
        if (birth == null) {
            missing.add(name);
        } else if (birth.lat() == null) {
            missing.add(name + ".lat/lon");
        }
    }

    static IllegalStateException missing(String type, List<String> missing) {
        return new IllegalStateException(type + ": missing required " + String.join(", ", missing));
    }
}
