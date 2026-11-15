package com.lifeastroapi;

import static com.lifeastroapi.Fixtures.ARJUN;
import static com.lifeastroapi.Fixtures.GIRL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeastroapi.params.AshtakavargaKakshaParams;
import com.lifeastroapi.params.AshtakavargaTransitScoreParams;
import com.lifeastroapi.params.CalendarRituParams;
import com.lifeastroapi.params.ChartDivisionalParams;
import com.lifeastroapi.params.ChartDoshasParams;
import com.lifeastroapi.params.ChartKundliFactsParams;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.ChartSensitivityParams;
import com.lifeastroapi.params.ChartYogasParams;
import com.lifeastroapi.params.DashaCharCurrentParams;
import com.lifeastroapi.params.FestivalsMonthParams;
import com.lifeastroapi.params.HoroscopeDailyParams;
import com.lifeastroapi.params.LalkitabDashaCurrentParams;
import com.lifeastroapi.params.MilanAshtakootaTotalParams;
import com.lifeastroapi.params.MilanDashaSyncParams;
import com.lifeastroapi.params.MilanMangalDoshaParams;
import com.lifeastroapi.params.MilanOverlayParams;
import com.lifeastroapi.params.MilanSummaryParams;
import com.lifeastroapi.params.MuhurtaBestTimeParams;
import com.lifeastroapi.params.NarrativeDashaPhalParams;
import com.lifeastroapi.params.NarrativeHoroscopeDailyByLagnaParams;
import com.lifeastroapi.params.NarrativeMilanParams;
import com.lifeastroapi.params.NarrativePrashnaParams;
import com.lifeastroapi.params.NarrativeSadeSatiHistoryParams;
import com.lifeastroapi.params.NarrativeSadeSatiPhaseParams;
import com.lifeastroapi.params.NarrativeSadeSatiStatusParams;
import com.lifeastroapi.params.NarrativeTransitDoubleParams;
import com.lifeastroapi.params.NarrativeTransitPhalParams;
import com.lifeastroapi.params.PanchangBasicParams;
import com.lifeastroapi.params.PanchangTithiParams;
import com.lifeastroapi.params.PlanetMomentsIngressParams;
import com.lifeastroapi.params.RemediesForGrahaParams;
import com.lifeastroapi.params.ReportsHoroscopeMonthlyParams;
import com.lifeastroapi.params.ReportsHoroscopeWeeklyParams;
import com.lifeastroapi.params.ReportsKundliLiteParams;
import com.lifeastroapi.params.ReportsMatchMakingParams;
import com.lifeastroapi.params.TransitSadeSatiWindowsParams;
import com.lifeastroapi.params.TransitSmallPanotiParams;
import com.lifeastroapi.params.TransitWindowsParams;
import com.lifeastroapi.params.WesternCompositeChartParams;
import com.lifeastroapi.params.WesternNatalChartParams;
import com.lifeastroapi.params.WesternReturnsSolarParams;
import com.lifeastroapi.params.WesternSynastryAspectsParams;
import com.lifeastroapi.params.WesternTransitsToNatalParams;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The wire names each input shape sends: the "param shapes", "endpoints corrected against the live
 * API", "natal chart + transit moment", "data endpoints", "params the manifest lacked" and
 * "optional calculation settings" cases of the Node SDK's client.test.ts.
 */
class ParamsTest {

    private final StubServer stub = new StubServer();
    private final LifeAstro client = stub.client().build();

    @AfterEach
    void stop() {
        client.close();
        stub.close();
    }

    private StubServer.Recorded call(Consumer<LifeAstro> c) {
        stub.reset();
        c.accept(client);
        return stub.last();
    }

    @Test
    void flatBirthParams() {
        Map<String, String> q = call(c -> c.chart().planets(ChartPlanetsParams.builder().birth(ARJUN).build())).query();
        assertEquals("1990-01-15", q.get("date"));
        assertEquals("10:30", q.get("time"));
        assertEquals("Asia/Kolkata", q.get("tz"));
        assertEquals("19.076", q.get("lat"));
        assertEquals("72.8777", q.get("lon"));
        assertFalse(q.containsKey("birth.date"));
    }

    @Test
    void flatSettersAndTheBirthConvenienceSendTheSame() {
        ChartPlanetsParams a = ChartPlanetsParams.builder().birth(ARJUN).build();
        ChartPlanetsParams b = ChartPlanetsParams.builder()
                .date(LocalDate.of(1990, 1, 15)).time(LocalTime.of(10, 30)).tz(ZoneId.of("Asia/Kolkata"))
                .lat(19.076).lon(72.8777).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toQuery(), b.toQuery());
        assertTrue(a.toString().contains("date=1990-01-15"), a.toString());
    }

    @Test
    void birthPrefixedParams() {
        StubServer.Recorded r = call(c -> c.milan().mangalDosha(MilanMangalDoshaParams.builder().birth(ARJUN).build()));
        assertEquals("/v1/milan/mangal-dosha", r.path());
        assertEquals("1990-01-15", r.query().get("birth.date"));
        assertEquals("19.076", r.query().get("birth.lat"));
        assertFalse(r.query().containsKey("date"));
    }

    @Test
    void boyGirlMatchmakingParams() {
        Map<String, String> q = call(c -> c.milan().ashtakootaTotal(MilanAshtakootaTotalParams.builder()
                .boy(ARJUN)
                .girl(BirthInput.of("1992-03-20", "10:30", "Asia/Kolkata", 19.076, 72.8777))
                .build())).query();
        assertEquals("1990-01-15", q.get("boy.date"));
        assertEquals("1992-03-20", q.get("girl.date"));
        assertEquals("72.8777", q.get("girl.lon"));
    }

    @Test
    void lowercasesRashi() {
        assertEquals("aries", call(c -> c.horoscope().daily(HoroscopeDailyParams.builder().rashi(" Aries ").build()))
                .query().get("rashi"));
    }

    @Test
    void substitutesPathParams() {
        StubServer.Recorded r = call(c -> c.chart().divisional(ChartDivisionalParams.builder().birth(ARJUN).varga("D9").build()));
        assertEquals("/v1/chart/divisional/D9", r.path());
        assertFalse(r.query().containsKey("varga"));
    }

    @Test
    void customShapeFestivalsMonth() {
        Map<String, String> q = call(c -> c.festivals().month(FestivalsMonthParams.builder()
                .year(2026).month(11).tz("Asia/Kolkata").lat(19.076).lon(72.8777)
                .locale(Lang.MR).monthSystem("amanta").build())).query();
        assertEquals("2026", q.get("year"));
        assertEquals("19.076", q.get("lat"));
        assertEquals("amanta", q.get("month_system"));
        assertEquals("mr", q.get("locale"));
    }

    @Test
    void placeConvenience() {
        PanchangBasicParams p = PanchangBasicParams.builder().place(Fixtures.MUMBAI).date("2027-01-15").build();
        assertEquals(19.076, p.lat());
        assertEquals("Asia/Kolkata", p.tz());
        FestivalsMonthParams f = FestivalsMonthParams.builder().year(2027).month(1).place(Fixtures.MUMBAI).build();
        assertEquals(72.8777, f.lon());
    }

    @Test
    void natalTransitShapeMapsNatalToBirthAndTransitFlat() {
        Map<String, String> q = call(c -> c.western().transits().toNatal(WesternTransitsToNatalParams.builder()
                .birth(ARJUN).date("2026-06-16").time("12:00").tz("Asia/Kolkata").build())).query();
        assertEquals("1990-01-15", q.get("birth.date"));
        assertEquals("2026-06-16", q.get("date"));
    }

    @Test
    void missingRequiredValuesFailInBuild() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ChartDivisionalParams.builder().birth(ARJUN).build());
        assertEquals("ChartDivisionalParams: missing required varga", e.getMessage());
        e = assertThrows(IllegalStateException.class, () -> PanchangBasicParams.builder().tz("UTC").build());
        assertEquals("PanchangBasicParams: missing required lat, lon", e.getMessage());
        e = assertThrows(IllegalStateException.class, () -> ChartDivisionalParams.builder().birth(ARJUN).varga("").build());
        assertTrue(e.getMessage().contains("varga"));
        e = assertThrows(IllegalStateException.class, () -> MilanAshtakootaTotalParams.builder()
                .boy(ARJUN).girl(BirthInput.withoutPlace("1992-03-20", "08:15", "Asia/Kolkata")).build());
        assertEquals("MilanAshtakootaTotalParams: missing required girl.lat/lon", e.getMessage());
    }

    @Test
    void personalisedHoroscopeSendsTheBirthChartUnprefixedAndTheDayAsTransitDate() {
        Map<String, String> q = call(c -> c.narrative().horoscopeDailyByLagna(NarrativeHoroscopeDailyByLagnaParams
                .builder().birth(ARJUN).onDate("2026-10-10").build())).query();
        assertEquals("1990-01-15", q.get("date"));
        assertEquals("2026-10-10", q.get("transit_date"));
        assertFalse(q.containsKey("birth.date"));
    }

    @Test
    void reportsAcceptTheNameAndPlaceForTheCoverPage() {
        Map<String, String> one = call(c -> c.reports().kundliLite(ReportsKundliLiteParams.builder()
                .birth(ARJUN).name("Arjun Mehta").place("Mumbai").build())).query();
        assertEquals("Arjun Mehta", one.get("name"));
        assertEquals("Mumbai", one.get("place"));
        Map<String, String> q = call(c -> c.reports().matchMaking(ReportsMatchMakingParams.builder()
                .boy(ARJUN).girl(ARJUN).boyName("A").girlName("B").boyPlace("Pune").build())).query();
        assertEquals("A", q.get("boy.name"));
        assertEquals("B", q.get("girl.name"));
        assertEquals("Pune", q.get("boy.place"));
    }

    @Test
    void monthlyHoroscopeReportTakesMonthAsYearMonth() {
        Map<String, String> q = call(c -> c.reports().horoscopeMonthly(ReportsHoroscopeMonthlyParams.builder()
                .rashi("Aries").month("2026-11").build())).query();
        assertEquals("2026-11", q.get("month"));
        assertFalse(q.containsKey("year"));
    }

    @Test
    void exposesTheMetaCatalogs() {
        stub.reset();
        client.meta().endpoints();
        client.meta().vedicYogas();
        assertEquals(List.of("/v1/meta/endpoints", "/v1/meta/vedic/yogas"),
                stub.calls().stream().map(StubServer.Recorded::path).toList());
    }

    @Test
    void natalChartAndTransitMomentAreSeparate() {
        record Case(String path, Consumer<LifeAstro> call, boolean place) {
        }
        String d = "2027-01-15";
        String t = "06:00";
        String tz = "Asia/Kolkata";
        List<Case> cases = List.of(
                new Case("/v1/vedic/narrative/transit-phal", c -> c.narrative().transitPhal(
                        NarrativeTransitPhalParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), true),
                new Case("/v1/vedic/narrative/dasha-phal", c -> c.narrative().dashaPhal(
                        NarrativeDashaPhalParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), true),
                new Case("/v1/vedic/narrative/transit-double", c -> c.narrative().transitDouble(
                        NarrativeTransitDoubleParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/vedic/narrative/sade-sati-status", c -> c.narrative().sadeSatiStatus(
                        NarrativeSadeSatiStatusParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/vedic/narrative/sade-sati-phase", c -> c.narrative().sadeSatiPhase(
                        NarrativeSadeSatiPhaseParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/vedic/narrative/sade-sati-history", c -> c.narrative().sadeSatiHistory(
                        NarrativeSadeSatiHistoryParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/ashtakavarga/kaksha", c -> c.ashtakavarga().kaksha(
                        AshtakavargaKakshaParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/ashtakavarga/transit-score", c -> c.ashtakavarga().transitScore(
                        AshtakavargaTransitScoreParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false),
                new Case("/v1/transit/small-panoti", c -> c.transit().smallPanoti(
                        TransitSmallPanotiParams.builder().birth(ARJUN).date(d).time(t).tz(tz).build()), false));
        for (Case k : cases) {
            StubServer.Recorded r = call(k.call());
            assertEquals(k.path(), r.path());
            Map<String, String> q = r.query();
            assertEquals("1990-01-15", q.get("birth.date"), k.path());
            assertEquals("10:30", q.get("birth.time"), k.path());
            assertEquals("19.076", q.get("birth.lat"), k.path());
            assertEquals("2027-01-15", q.get("date"), k.path());
            assertEquals("Asia/Kolkata", q.get("tz"), k.path());
            // Endpoints that cast the natal lagna read the birth place unprefixed.
            assertEquals(k.place() ? "19.076" : null, q.get("lat"), k.path());
        }
    }

    @Test
    void narrativePrashnaSendsTheQuestionMomentTopicAndHouse() {
        StubServer.Recorded r = call(c -> c.narrative().prashna(NarrativePrashnaParams.builder()
                .lat(19.076).lon(72.8777).tz("Asia/Kolkata").date("2027-01-15").time("10:00")
                .topic("career").questionHouse(10).lang(Lang.HI).build()));
        assertEquals("/v1/vedic/narrative/prashna", r.path());
        Map<String, String> q = r.query();
        assertEquals("2027-01-15", q.get("date"));
        assertEquals("10:00", q.get("time"));
        assertEquals("career", q.get("topic"));
        assertEquals("10", q.get("question_house"));
        assertEquals("hi", q.get("locale"));
    }

    @Test
    void chartDataEndpointsReadTheBirthChartUnprefixed() {
        record Case(String path, Consumer<LifeAstro> call, Map<String, String> extra) {
        }
        List<Case> cases = List.of(
                new Case("/v1/chart/kundli-facts", c -> c.chart().kundliFacts(ChartKundliFactsParams.builder()
                        .birth(ARJUN).vargas("D9,D10").include("all").gender("male").build()),
                        Map.of("vargas", "D9,D10", "include", "all", "gender", "male")),
                new Case("/v1/chart/yogas", c -> c.chart().yogas(ChartYogasParams.builder().birth(ARJUN).all(true).build()),
                        Map.of("all", "true")),
                new Case("/v1/chart/doshas", c -> c.chart().doshas(ChartDoshasParams.builder().birth(ARJUN).gender("female").build()),
                        Map.of("gender", "female")),
                new Case("/v1/chart/sensitivity", c -> c.chart().sensitivity(ChartSensitivityParams.builder()
                        .birth(ARJUN).window(60).vargas("D9").topocentricMoon(true).build()),
                        Map.of("window", "60", "vargas", "D9", "topocentric_moon", "true")));
        for (Case k : cases) {
            StubServer.Recorded r = call(k.call());
            assertEquals(k.path(), r.path());
            Map<String, String> q = r.query();
            assertEquals("1990-01-15", q.get("date"));
            assertEquals("10:30", q.get("time"));
            assertEquals("19.076", q.get("lat"));
            assertNull(q.get("birth.date"));
            k.extra().forEach((key, v) -> assertEquals(v, q.get(key), k.path() + " " + key));
        }
    }

    @Test
    void sadeSatiWindowsSendsBirthPlusUnprefixedFromAndTz() {
        StubServer.Recorded r = call(c -> c.transit().sadeSatiWindows(TransitSadeSatiWindowsParams.builder()
                .birth(BirthInput.withoutPlace("1990-01-15", "10:30", "Asia/Kolkata"))
                .from("2027-01-15").tz("UTC").years(30).includePast(true).build()));
        assertEquals("/v1/transit/sade-sati/windows", r.path());
        Map<String, String> q = r.query();
        assertEquals("1990-01-15", q.get("birth.date"));
        assertEquals("10:30", q.get("birth.time"));
        assertEquals("Asia/Kolkata", q.get("birth.tz"));
        assertNull(q.get("birth.lat"));
        assertNull(q.get("date"));
        assertEquals("2027-01-15", q.get("from"));
        assertEquals("UTC", q.get("tz"));
        assertEquals("30", q.get("years"));
        assertEquals("true", q.get("include_past"));
    }

    @Test
    void transitWindowsPlanetRequiredBirthOptional() {
        Map<String, String> q = call(c -> c.transit().windows(TransitWindowsParams.builder()
                .planet("jupiter").from("2027-01-15").years(12).build())).query();
        assertEquals("jupiter", q.get("planet"));
        assertEquals("2027-01-15", q.get("from"));
        assertEquals("12", q.get("years"));
        assertFalse(q.keySet().stream().anyMatch(k -> k.startsWith("birth.")));
        Map<String, String> q2 = call(c -> c.transit().windows(TransitWindowsParams.builder()
                .planet("saturn").birth(ARJUN).build())).query();
        assertEquals("1990-01-15", q2.get("birth.date"));
        assertEquals("19.076", q2.get("birth.lat"));
        assertEquals("72.8777", q2.get("birth.lon"));
        assertNull(q2.get("lat"));
    }

    @Test
    void remediesForGrahaMapsGrahaReasonIncludeCategoryAndLanguage() {
        StubServer.Recorded r = call(c -> c.remedies().forGraha(RemediesForGrahaParams.builder()
                .graha("saturn").reason("dosha:sade_sati").include("pooja,yantra").category("mantra").lang(Lang.HI).build()));
        assertEquals("/v1/vedic/remedies/for-graha", r.path());
        Map<String, String> q = r.query();
        assertEquals("saturn", q.get("graha"));
        assertEquals("dosha:sade_sati", q.get("reason"));
        assertEquals("pooja,yantra", q.get("include"));
        assertEquals("mantra", q.get("category"));
        assertEquals("hi", q.get("locale"));
    }

    @Test
    void milanOverlaySummaryAndDashaSyncSendBoyAndGirl() {
        StubServer.Recorded r = call(c -> c.milan().overlay(MilanOverlayParams.builder().boy(ARJUN).girl(GIRL).build()));
        assertEquals("/v1/milan/overlay", r.path());
        assertEquals("1990-01-15", r.query().get("boy.date"));
        assertEquals("1992-03-20", r.query().get("girl.date"));
        assertEquals("28.6139", r.query().get("girl.lat"));
        r = call(c -> c.milan().summary(MilanSummaryParams.builder().boy(ARJUN).girl(GIRL)
                .from("2027-01-15").tz("Asia/Kolkata").include("kundli_facts").build()));
        assertEquals("/v1/milan/summary", r.path());
        assertEquals("10:30", r.query().get("boy.time"));
        assertEquals("08:15", r.query().get("girl.time"));
        assertEquals("kundli_facts", r.query().get("include"));
        r = call(c -> c.milan().dashaSync(MilanDashaSyncParams.builder().boy(ARJUN).girl(GIRL).timeline(true).years(5)
                .from("2027-01-15").marriageWindows(true).date("2027-02-01").tz("Asia/Kolkata").build()));
        Map<String, String> q = r.query();
        assertEquals("/v1/milan/dasha-sync", r.path());
        assertEquals("19.076", q.get("boy.lat"));
        assertEquals("77.209", q.get("girl.lon"));
        assertEquals("true", q.get("timeline"));
        assertEquals("5", q.get("years"));
        assertEquals("true", q.get("marriage_windows"));
        assertEquals("2027-02-01", q.get("date"));
    }

    @Test
    void narrativeMilanBoyAndGirlConveniences() {
        Map<String, String> q = call(c -> c.narrative().milan(NarrativeMilanParams.builder()
                .boy(ARJUN).girl(GIRL).build())).query();
        assertEquals("1990-01-15", q.get("boy.date"));
        assertEquals("08:15", q.get("girl.time"));
        assertNull(q.get("boy.lat"));
    }

    @Test
    void paramsTheManifestLackedAreSent() {
        Map<String, String> q = call(c -> c.muhurta().bestTime(MuhurtaBestTimeParams.builder()
                .place(Fixtures.MUMBAI).startDate("2027-01-15").endDate("2027-01-31")
                .purpose("vivah").topN(3).birthDate("1990-01-15").build())).query();
        assertEquals("vivah", q.get("purpose"));
        assertEquals("3", q.get("top_n"));
        assertEquals("1990-01-15", q.get("birth_date"));
        assertEquals("2027-01-15", q.get("start_date"));
        StubServer.Recorded r = call(c -> c.dasha().charCurrent(DashaCharCurrentParams.builder()
                .birth(ARJUN).at("2027-01-15T06:00:00Z").build()));
        assertEquals("/v1/dasha/char/current", r.path());
        assertEquals("2027-01-15T06:00:00Z", r.query().get("at"));
        r = call(c -> c.lalkitab().dashaCurrent(LalkitabDashaCurrentParams.builder().birth(ARJUN).at("2027-01-15").build()));
        assertEquals("/v1/lalkitab/dasha/current", r.path());
        r = call(c -> c.reports().horoscopeWeekly(ReportsHoroscopeWeeklyParams.builder().rashi("Aries").week("2026-W47").build()));
        assertEquals("2026-W47", r.query().get("week"));
        assertEquals("aries", r.query().get("rashi"));
    }

    @Test
    void optionalCalculationSettings() {
        Map<String, String> q = call(c -> c.western().natal().chart(WesternNatalChartParams.builder()
                .birth(ARJUN).orbTrine(6).orbSextile(4).rulership("traditional").build())).query();
        assertEquals("6", q.get("orb_trine"));
        assertEquals("4", q.get("orb_sextile"));
        assertEquals("traditional", q.get("rulership"));
        q = call(c -> c.western().synastry().aspects(WesternSynastryAspectsParams.builder()
                .personA(ARJUN).personB(GIRL).houseSystem(HouseSystem.KOCH).orbConjunction(8).build())).query();
        assertEquals("koch", q.get("house_system"));
        assertEquals("8", q.get("orb_conjunction"));
        assertEquals("1990-01-15", q.get("personA.date"));
        q = call(c -> c.western().transits().toNatal(WesternTransitsToNatalParams.builder()
                .birth(ARJUN).date("2027-01-15").time("06:00").tz("UTC").orbSquare(5).build())).query();
        assertEquals("5", q.get("orb_square"));
        q = call(c -> c.western().composite().chart(WesternCompositeChartParams.builder()
                .personA(ARJUN).personB(GIRL).compositeMethod("midpoint_cusps").build())).query();
        assertEquals("midpoint_cusps", q.get("composite_method"));
        q = call(c -> c.western().returns().solar(WesternReturnsSolarParams.builder()
                .birth(ARJUN).returnYear(2027).relocateLat(51.5).relocateLon(-0.12).build())).query();
        assertEquals("51.5", q.get("relocate_lat"));
        assertEquals("-0.12", q.get("relocate_lon"));
        q = call(c -> c.calendar().ritu(CalendarRituParams.builder().date("2027-01-15").tz("Asia/Kolkata")
                .ayanamsa(Ayanamsa.RAMAN).build())).query();
        assertEquals("raman", q.get("ayanamsa"));
        q = call(c -> c.planetMoments().ingress(PlanetMomentsIngressParams.builder().planet("Jupiter")
                .date("2027-01-15").tz("Asia/Kolkata").ayanamsa("krishnamurti").build())).query();
        assertEquals("krishnamurti", q.get("ayanamsa"));
        q = call(c -> c.chart().planets(ChartPlanetsParams.builder().birth(ARJUN)
                .ayanamsa(Ayanamsa.KRISHNAMURTI).houseSystem(HouseSystem.PLACIDUS).build())).query();
        assertEquals("placidus", q.get("house_system"));
    }

    @Test
    void dateDefaultsToTodayForMomentEndpoints() {
        String q = call(c -> c.panchang().tithi(PanchangTithiParams.builder().place(Fixtures.MUMBAI).build()))
                .query().get("date");
        assertTrue(q.matches("\\d{4}-\\d{2}-\\d{2}"), q);
        assertEquals("2026-11-20", call(c -> c.panchang().tithi(PanchangTithiParams.builder()
                .place(Fixtures.MUMBAI).date("2026-11-20").build())).query().get("date"));
    }

    @Test
    void transitPositionsNeedsNoArguments() {
        Map<String, String> q = call(c -> c.transit().positions()).query();
        assertEquals("UTC", q.get("tz"));
        assertTrue(q.get("date").matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void javaTimeOverloadsFormatLikeTheApiExpects() {
        PanchangTithiParams p = PanchangTithiParams.builder().lat(1).lon(2).tz(ZoneId.of("+05:30"))
                .date(LocalDate.of(2027, 1, 5)).time(LocalTime.of(6, 5, 9)).build();
        assertEquals("+05:30", p.tz());
        assertEquals("2027-01-05", p.date());
        assertEquals("06:05:09", p.time());
        assertEquals("10:30", BirthInput.of(LocalDate.of(1990, 1, 15), LocalTime.of(10, 30),
                ZoneId.of("Asia/Kolkata"), 19.076, 72.8777).time());
    }

    @Test
    void paramsAreValueObjects() {
        PanchangBasicParams a = PanchangBasicParams.builder().place(Fixtures.MUMBAI).date("2027-01-15").lang(Lang.GU).build();
        PanchangBasicParams b = PanchangBasicParams.builder().lat(19.076).lon(72.8777).tz("Asia/Kolkata")
                .date("2027-01-15").lang("gu").build();
        PanchangBasicParams c = PanchangBasicParams.builder().place(Fixtures.MUMBAI).date("2027-01-16").lang(Lang.GU).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertEquals("gu", a.lang());
        assertEquals("/v1/panchang/basic", a.toPath());
        assertEquals(Map.of("lat", 19.076, "lon", 72.8777, "tz", "Asia/Kolkata", "date", "2027-01-15",
                "lang", "gu", "locale", "gu"), a.toQuery());
    }
}
