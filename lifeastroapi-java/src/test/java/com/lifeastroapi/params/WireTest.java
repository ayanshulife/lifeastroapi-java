package com.lifeastroapi.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The helpers behind the generated parameter classes. */
class WireTest {

    @Test
    void todayInResolvesTheDateInTheZoneAndFallsBackToUtc() {
        Instant now = Instant.parse("2026-03-01T20:00:00Z"); // already 2 March in Kolkata
        assertEquals("2026-03-02", Wire.todayIn("Asia/Kolkata", now));
        assertEquals("2026-03-01", Wire.todayIn("America/Los_Angeles", now));
        assertEquals("2026-03-02", Wire.todayIn("+05:30", now));
        assertEquals("2026-03-01", Wire.todayIn("Not/AZone", now));
        assertEquals("2026-03-01", Wire.todayIn("5.5", now));
        assertEquals("2026-03-01", Wire.todayIn(null, now));
    }

    @Test
    void encodesLikeEncodeUriComponent() {
        assertEquals("a%20b%2Fc", Wire.encodeUriComponent("a b/c"));
        assertEquals("!'()*~-_.", Wire.encodeUriComponent("!'()*~-_."));
        assertEquals("vedic.L1.remedy.yantra.jupiter", Wire.encodeUriComponent("vedic.L1.remedy.yantra.jupiter"));
        assertEquals("%2B05%3A30", Wire.encodeUriComponent("+05:30"));
    }

    @Test
    void fillsPathTemplates() {
        assertEquals("/v1/dasha/vimshottari/Jupiter/Saturn", Wire.path("/v1/dasha/vimshottari/{md}/{ad}", "md", "Jupiter", "ad", "Saturn"));
        assertThrows(IllegalArgumentException.class, () -> Wire.path("/v1/x/{id}", "id", ""));
    }
}
