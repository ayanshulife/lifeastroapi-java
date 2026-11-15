package com.lifeastroapi;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * A birth (or event) moment and its place: the chart input of {@code chart}, {@code dasha},
 * {@code milan} ({@code boy} / {@code girl}), transit-style methods ({@code birth}) and Western
 * pairs ({@code personA} / {@code personB}). Each method sends it under the wire names the API
 * reads, exactly as the Node.js SDK does: unprefixed {@code date}, {@code time}, {@code tz},
 * {@code lat}, {@code lon} for a chart, {@code birth.*} for the natal chart of a transit, and so
 * on.
 *
 * <pre>{@code
 * BirthInput arjun = BirthInput.of("1990-01-15", "10:30", "Asia/Kolkata", 19.076, 72.8777);
 * }</pre>
 *
 * <p>{@code lat} and {@code lon} may be left out only for the window methods
 * ({@code transit().sadeSatiWindows}, {@code transit().windows}), which need just the natal Moon;
 * see {@link #withoutPlace(String, String, String)}.
 *
 * @param date birth date, {@code YYYY-MM-DD}
 * @param time local birth time, 24-hour {@code HH:MM}
 * @param tz   time zone: an IANA name ({@code Asia/Kolkata}) or a UTC offset ({@code +05:30})
 * @param lat  latitude in decimal degrees, north positive; null only for window methods
 * @param lon  longitude in decimal degrees, east positive; null only for window methods
 */
public record BirthInput(String date, String time, String tz, Double lat, Double lon) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Checks the values: {@code date}, {@code time} and {@code tz} are required, and {@code lat}
     * and {@code lon} are given together.
     *
     * @param date birth date
     * @param time birth time
     * @param tz   time zone
     * @param lat  latitude, or null
     * @param lon  longitude, or null
     */
    public BirthInput {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(tz, "tz");
        if ((lat == null) != (lon == null)) {
            throw new IllegalArgumentException("lat and lon must be given together");
        }
        if (lat != null && (!Double.isFinite(lat) || !Double.isFinite(lon))) {
            throw new IllegalArgumentException("lat and lon must be finite numbers");
        }
    }

    /**
     * A birth moment from plain values.
     *
     * @param date birth date, {@code YYYY-MM-DD}
     * @param time local birth time, {@code HH:MM}
     * @param tz   an IANA name ({@code Asia/Kolkata}) or a UTC offset ({@code +05:30})
     * @param lat  latitude in decimal degrees, north positive
     * @param lon  longitude in decimal degrees, east positive
     * @return the birth input
     */
    public static BirthInput of(String date, String time, String tz, double lat, double lon) {
        return new BirthInput(date, time, tz, lat, lon);
    }

    /**
     * A birth moment from {@code java.time} values, for example the columns of a JPA entity.
     * The time is sent as {@code HH:MM}, or {@code HH:MM:SS} when it has seconds.
     *
     * @param date birth date
     * @param time local birth time
     * @param zone time zone; its id is sent ({@code Asia/Kolkata}, {@code +05:30})
     * @param lat  latitude in decimal degrees, north positive
     * @param lon  longitude in decimal degrees, east positive
     * @return the birth input
     */
    public static BirthInput of(LocalDate date, LocalTime time, ZoneId zone, double lat, double lon) {
        return new BirthInput(formatDate(date), formatTime(time), Objects.requireNonNull(zone, "zone").getId(),
                lat, lon);
    }

    /**
     * A birth moment at a {@link Place}.
     *
     * @param date  birth date, {@code YYYY-MM-DD}
     * @param time  local birth time, {@code HH:MM}
     * @param place where the birth took place (its {@code tz} is the birth time zone)
     * @return the birth input
     */
    public static BirthInput of(String date, String time, Place place) {
        Objects.requireNonNull(place, "place");
        return new BirthInput(date, time, place.tz(), place.lat(), place.lon());
    }

    /**
     * A birth moment without a place, for the window methods that need only the natal Moon
     * ({@code transit().sadeSatiWindows}, {@code transit().windows}).
     *
     * @param date birth date, {@code YYYY-MM-DD}
     * @param time local birth time, {@code HH:MM}
     * @param tz   an IANA name or a UTC offset
     * @return the birth input with no {@code lat} / {@code lon}
     */
    public static BirthInput withoutPlace(String date, String time, String tz) {
        return new BirthInput(date, time, tz, null, null);
    }

    /**
     * The place of this birth, or null when it has no coordinates.
     *
     * @return the place, or null
     */
    public Place place() {
        return lat == null ? null : new Place(lat, lon, tz);
    }

    static String formatDate(LocalDate date) {
        return Objects.requireNonNull(date, "date").toString();
    }

    static String formatTime(LocalTime time) {
        Objects.requireNonNull(time, "time");
        return (time.getSecond() == 0 && time.getNano() == 0 ? HH_MM : HH_MM_SS).format(time);
    }
}
