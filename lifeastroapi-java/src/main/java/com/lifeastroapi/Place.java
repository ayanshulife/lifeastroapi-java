package com.lifeastroapi;

import java.time.ZoneId;
import java.util.Objects;

/**
 * A location and its time zone, for panchang-style methods ({@code lat}, {@code lon}, {@code tz}).
 *
 * <pre>{@code
 * Place mumbai = Place.of(19.076, 72.8777, "Asia/Kolkata");
 * client.panchang().basic(PanchangBasicParams.builder().place(mumbai).build());
 * }</pre>
 *
 * @param lat latitude in decimal degrees, north positive
 * @param lon longitude in decimal degrees, east positive
 * @param tz  time zone: an IANA name ({@code Asia/Kolkata}) or a UTC offset ({@code +05:30})
 */
public record Place(double lat, double lon, String tz) {

    /**
     * Checks the values.
     *
     * @param lat latitude in decimal degrees
     * @param lon longitude in decimal degrees
     * @param tz  time zone
     */
    public Place {
        Objects.requireNonNull(tz, "tz");
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            throw new IllegalArgumentException("lat and lon must be finite numbers");
        }
    }

    /**
     * A place from its coordinates and time zone name.
     *
     * @param lat latitude in decimal degrees, north positive
     * @param lon longitude in decimal degrees, east positive
     * @param tz  an IANA name ({@code Asia/Kolkata}) or a UTC offset ({@code +05:30})
     * @return the place
     */
    public static Place of(double lat, double lon, String tz) {
        return new Place(lat, lon, tz);
    }

    /**
     * A place from its coordinates and a {@link ZoneId}.
     *
     * @param lat  latitude in decimal degrees, north positive
     * @param lon  longitude in decimal degrees, east positive
     * @param zone the time zone; its id is sent ({@code Asia/Kolkata}, {@code +05:30})
     * @return the place
     */
    public static Place of(double lat, double lon, ZoneId zone) {
        return new Place(lat, lon, Objects.requireNonNull(zone, "zone").getId());
    }
}
