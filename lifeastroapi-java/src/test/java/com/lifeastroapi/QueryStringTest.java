package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Numbers and escaping exactly as JavaScript's {@code String(n)} and {@code URLSearchParams}. */
class QueryStringTest {

    @Test
    void numbersAsJavaScriptWritesThem() {
        assertEquals("19.076", QueryString.formatNumber(19.076));
        assertEquals("72.8777", QueryString.formatNumber(72.8777));
        assertEquals("5", QueryString.formatNumber(5.0));
        assertEquals("100", QueryString.formatNumber(100.0));
        assertEquals("-0.12", QueryString.formatNumber(-0.12));
        assertEquals("0", QueryString.formatNumber(-0.0));
        assertEquals("0.000001", QueryString.formatNumber(0.000001));
        assertEquals("0.0000025", QueryString.formatNumber(2.5e-6));
        assertEquals("1e-7", QueryString.formatNumber(1e-7));
        assertEquals("1.5e+21", QueryString.formatNumber(1.5e21));
        assertEquals("1e+21", QueryString.formatNumber(1e21));
        assertEquals("123456789012345680000", QueryString.formatNumber(1.2345678901234568e20));
        assertEquals("0.30000000000000004", QueryString.formatNumber(0.1 + 0.2));
        assertEquals("2461420.572762", QueryString.formatNumber(2461420.572762));
        assertEquals("NaN", QueryString.formatNumber(Double.NaN));
        assertEquals("-Infinity", QueryString.formatNumber(Double.NEGATIVE_INFINITY));
    }

    @Test
    void encodesSortedSkippingEmptyValues() {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("tz", "Asia/Kolkata");
        q.put("birth.lat", 19.076);
        q.put("all", true);
        q.put("empty", "");
        q.put("none", null);
        q.put("name", "Arjun Mehta");
        q.put("q", "*-._~!'()");
        assertEquals("?all=true&birth.lat=19.076&name=Arjun+Mehta&q=*-._%7E%21%27%28%29&tz=Asia%2FKolkata", QueryString.encode(q));
        assertEquals("", QueryString.encode(Map.of("a", "")));
        assertEquals("", QueryString.encode(null));
    }

    @Test
    void unicodeIsUtf8PercentEncoded() {
        assertEquals("?q=%E0%A4%AE%E0%A5%81%E0%A4%82%E0%A4%AC%E0%A4%88", QueryString.encode(Map.of("q", "मुंबई")));
    }
}
