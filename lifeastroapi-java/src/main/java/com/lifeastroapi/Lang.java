package com.lifeastroapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Response language, passed as {@code lang} on every Vedic method that has it (panchang, calendar,
 * festivals, eclipses, planet moments, transit, milan, muhurta, chart, prashna, numerology, dasha,
 * varshaphal, ashtakavarga, Lal Kitab, horoscope, reports, narrative, remedies).
 *
 * <p>Names, terms and readings come back in the language; ids, dates, numbers and explicit
 * {@code *_en} / {@code *_hi} fields stay as they are. Western, tarot, geo and meta methods answer
 * in English (Western narrative also in Hindi and Marathi).
 */
public enum Lang {
    /** English (the default). */
    EN("en", "English", true),
    /** Hindi. */
    HI("hi", "Hindi", true),
    /** Marathi. */
    MR("mr", "Marathi", true),
    /** Gujarati. */
    GU("gu", "Gujarati", true),
    /** Bengali. */
    BN("bn", "Bengali", true),
    /** Telugu. */
    TE("te", "Telugu", true),
    /** Tamil. */
    TA("ta", "Tamil", true),
    /** Kannada. */
    KN("kn", "Kannada", true),
    /** Malayalam. */
    ML("ml", "Malayalam", true),
    /** Punjabi: names and terms in Punjabi; the longer readings are still in English. */
    PA("pa", "Punjabi", false);

    private final String code;
    private final String englishName;
    private final boolean complete;

    Lang(String code, String englishName, boolean complete) {
        this.code = code;
        this.englishName = englishName;
        this.complete = complete;
    }

    /**
     * The code sent on the wire, e.g. {@code "gu"}.
     *
     * @return the ISO 639-1 code
     */
    @JsonValue
    public String code() {
        return code;
    }

    /**
     * The language's English name, e.g. {@code "Gujarati"}.
     *
     * @return the English name
     */
    public String englishName() {
        return englishName;
    }

    /**
     * Whether every reading is in this language. False for {@link #PA}, whose longer readings are
     * still in English.
     *
     * @return true when names, terms and readings are all translated
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * The language for a code or a constant name, in any case: {@code "gu"}, {@code "GU"}.
     *
     * @param code a language code such as {@code "hi"}
     * @return the matching language
     * @throws IllegalArgumentException when the code is not one of the ten languages
     */
    @JsonCreator
    public static Lang fromCode(String code) {
        if (code != null) {
            String c = code.trim().toLowerCase(Locale.ROOT);
            for (Lang lang : values()) {
                if (lang.code.equals(c)) {
                    return lang;
                }
            }
        }
        throw new IllegalArgumentException("Unknown language \"" + code + "\"; use one of "
                + Arrays.stream(values()).map(Lang::code).collect(Collectors.joining(", ")));
    }
}
