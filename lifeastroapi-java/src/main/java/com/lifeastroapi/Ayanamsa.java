package com.lifeastroapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Sidereal ayanamsa for Vedic calculations. The API uses {@link #LAHIRI} when none is given. Methods
 * that take an ayanamsa also accept its plain value as a string.
 */
public enum Ayanamsa {
    /** Lahiri (Chitrapaksha), the default. */
    LAHIRI("lahiri"),
    /** Krishnamurti (KP). */
    KRISHNAMURTI("krishnamurti"),
    /** B. V. Raman. */
    RAMAN("raman"),
    /** Fagan-Bradley. */
    FAGAN("fagan"),
    /** Sri Yukteshwar. */
    YUKTESHWAR("yukteshwar"),
    /** True Chitrapaksha. */
    TRUE_CHITRAPAKSHA("true_chitrapaksha");

    private final String value;

    Ayanamsa(String value) {
        this.value = value;
    }

    /**
     * The value sent on the wire, e.g. {@code "lahiri"}.
     *
     * @return the wire value
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * The ayanamsa for a wire value or constant name, in any case.
     *
     * @param value a value such as {@code "krishnamurti"}
     * @return the matching ayanamsa
     * @throws IllegalArgumentException when the value is unknown
     */
    @JsonCreator
    public static Ayanamsa fromValue(String value) {
        if (value != null) {
            String v = value.trim().toLowerCase(Locale.ROOT);
            for (Ayanamsa a : values()) {
                if (a.value.equals(v)) {
                    return a;
                }
            }
        }
        throw new IllegalArgumentException("Unknown ayanamsa \"" + value + "\"");
    }
}
