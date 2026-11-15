package com.lifeastroapi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Query string encoding, byte for byte as the Node.js SDK's {@code encodeQuery}: keys sorted, null
 * and empty values left out, numbers written the way JavaScript's {@code String(n)} writes them,
 * booleans {@code true} / {@code false}, and {@code application/x-www-form-urlencoded} escaping as
 * {@code URLSearchParams} does it.
 */
final class QueryString {

    private QueryString() {
    }

    static String encode(Map<String, ?> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringJoiner out = new StringJoiner("&", "?", "");
        out.setEmptyValue("");
        for (Map.Entry<String, ?> e : new TreeMap<String, Object>(query).entrySet()) {
            Object value = e.getValue();
            if (value == null || "".equals(value)) {
                continue;
            }
            out.add(form(e.getKey()) + "=" + form(toWire(value)));
        }
        return out.toString();
    }

    /** The text a query value is sent as. */
    static String toWire(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Double d) {
            return formatNumber(d);
        }
        if (value instanceof Float f) {
            return formatNumber(Double.parseDouble(Float.toString(f)));
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Lang l) {
            return l.code();
        }
        if (value instanceof Ayanamsa a) {
            return a.value();
        }
        if (value instanceof HouseSystem h) {
            return h.value();
        }
        if (value instanceof Enum<?> en) {
            return en.name();
        }
        return String.valueOf(value);
    }

    /**
     * A double as JavaScript's {@code Number.prototype.toString} writes it: {@code 19.076},
     * {@code 5} (not {@code 5.0}), {@code 0.000001}, {@code 1e-7}, {@code 1.5e+21}.
     */
    static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0.0) {
            return "0";
        }
        // Shortest round-trip digits, then laid out by the ECMAScript Number::toString rules.
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = bd.unscaledValue().toString();
        int k = digits.length();
        int n = k - bd.scale(); // value = 0.<digits> x 10^n
        String sign = value < 0 ? "-" : "";
        if (k <= n && n <= 21) {
            return sign + digits + "0".repeat(n - k);
        }
        if (0 < n && n <= 21) {
            return sign + digits.substring(0, n) + "." + digits.substring(n);
        }
        if (-6 < n && n <= 0) {
            return sign + "0." + "0".repeat(-n) + digits;
        }
        int e = n - 1;
        String mantissa = k == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return sign + mantissa + "e" + (e < 0 ? "-" : "+") + Math.abs(e);
    }

    /** {@code application/x-www-form-urlencoded}, as JavaScript's {@code URLSearchParams}. */
    static String form(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
