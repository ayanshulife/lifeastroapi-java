package com.lifeastroapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * House system, sent as {@code house_system}. Methods that take a house system also accept its
 * plain value as a string.
 */
public enum HouseSystem {
    /** Whole sign. */
    WHOLE_SIGN("whole-sign"),
    /** Equal house. */
    EQUAL("equal"),
    /** Placidus. */
    PLACIDUS("placidus"),
    /** Koch. */
    KOCH("koch"),
    /** Porphyry. */
    PORPHYRY("porphyry"),
    /** Campanus. */
    CAMPANUS("campanus"),
    /** Regiomontanus. */
    REGIOMONTANUS("regiomontanus");

    private final String value;

    HouseSystem(String value) {
        this.value = value;
    }

    /**
     * The value sent on the wire, e.g. {@code "whole-sign"}.
     *
     * @return the wire value
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * The house system for a wire value or constant name, in any case ({@code "placidus"},
     * {@code "WHOLE_SIGN"}).
     *
     * @param value a value such as {@code "koch"}
     * @return the matching house system
     * @throws IllegalArgumentException when the value is unknown
     */
    @JsonCreator
    public static HouseSystem fromValue(String value) {
        if (value != null) {
            String v = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (HouseSystem h : values()) {
                if (h.value.equals(v)) {
                    return h;
                }
            }
        }
        throw new IllegalArgumentException("Unknown house system \"" + value + "\"");
    }
}
