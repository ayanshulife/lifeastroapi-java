package com.lifeastroapi;

import static com.lifeastroapi.Fixtures.ARJUN;
import static com.lifeastroapi.Fixtures.GIRL;
import static com.lifeastroapi.Fixtures.MUMBAI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeastroapi.params.AshtakavargaBhinnaParams;
import com.lifeastroapi.params.CalendarRituParams;
import com.lifeastroapi.params.ChartKundliFactsParams;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.ChartYogasParams;
import com.lifeastroapi.params.DashaVimshottariParams;
import com.lifeastroapi.params.EclipsesAllParams;
import com.lifeastroapi.params.FestivalsMonthParams;
import com.lifeastroapi.params.GeoSearchParams;
import com.lifeastroapi.params.HoroscopeDailyParams;
import com.lifeastroapi.params.LalkitabChartParams;
import com.lifeastroapi.params.MilanAshtakootaParams;
import com.lifeastroapi.params.MuhurtaBestTimeParams;
import com.lifeastroapi.params.MuhurtaVivahParams;
import com.lifeastroapi.params.NarrativeLagnaParams;
import com.lifeastroapi.params.NarrativeYearlyBhavishyafalParams;
import com.lifeastroapi.params.NumerologyDriverParams;
import com.lifeastroapi.params.NumerologyFullParams;
import com.lifeastroapi.params.PanchangBasicParams;
import com.lifeastroapi.params.PanchangMonthlyParams;
import com.lifeastroapi.params.PanchangPanchakaParams;
import com.lifeastroapi.params.PanchangRahuKaalParams;
import com.lifeastroapi.params.PanchangTithiParams;
import com.lifeastroapi.params.PlanetMomentsIngressParams;
import com.lifeastroapi.params.PrashnaChartParams;
import com.lifeastroapi.params.RemediesForGrahaParams;
import com.lifeastroapi.params.ReportsKundliLiteParams;
import com.lifeastroapi.params.TarotDailyParams;
import com.lifeastroapi.params.TransitPositionsParams;
import com.lifeastroapi.params.TransitTarabalaParams;
import com.lifeastroapi.params.TransitWindowsParams;
import com.lifeastroapi.params.VarshaphalChartParams;
import com.lifeastroapi.params.WesternNarrativeSabianParams;
import com.lifeastroapi.params.WesternNatalPlanetsParams;
import com.lifeastroapi.params.WesternSynastryAspectsParams;
import com.lifeastroapi.params.WesternTransitsPositionsParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "localization" cases of the Node SDK's client.test.ts. */
class LanguageTest {

    private final StubServer stub = new StubServer();
    private final LifeAstro client = stub.client().build();

    private static final String D = "2027-01-15";

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
    void sendsBothLangAndLocaleForNarrativeEndpoints() {
        Map<String, String> q = call(c -> c.narrative().lagna(NarrativeLagnaParams.builder().birth(ARJUN).lang(Lang.HI).build())).query();
        assertEquals("hi", q.get("lang"));
        assertEquals("hi", q.get("locale"));
    }

    /** One method per Vedic group; {@code both} is false where the shape's own builder sends {@code lang} only. */
    record Case(String name, String path, Consumer<LifeAstro> call, boolean both) {
    }

    @Test
    void everyVedicGroupSendsTheLanguage() {
        Lang gu = Lang.GU;
        List<Case> cases = List.of(
                new Case("panchang.basic", "/v1/panchang/basic",
                        c -> c.panchang().basic(PanchangBasicParams.builder().place(MUMBAI).date(D).lang(gu).build()), true),
                new Case("panchang.panchaka", "/v1/panchang/panchaka",
                        c -> c.panchang().panchaka(PanchangPanchakaParams.builder().place(MUMBAI).date(D).lang(gu).build()), true),
                new Case("calendar.ritu", "/v1/calendar/ritu",
                        c -> c.calendar().ritu(CalendarRituParams.builder().date(D).tz("Asia/Kolkata").lang(gu).build()), true),
                new Case("festivals.month", "/v1/festivals/month",
                        c -> c.festivals().month(FestivalsMonthParams.builder().year(2027).month(1).lang(gu).build()), true),
                new Case("eclipses.all", "/v1/eclipses/all",
                        c -> c.eclipses().all(EclipsesAllParams.builder().startDate("2027-01-01").endDate("2027-12-31").lang(gu).build()), true),
                new Case("planetMoments.ingress", "/v1/planet-moments/ingress",
                        c -> c.planetMoments().ingress(PlanetMomentsIngressParams.builder().planet("Jupiter").date(D).tz("Asia/Kolkata").lang(gu).build()), true),
                new Case("transit.positions", "/v1/transit/positions",
                        c -> c.transit().positions(TransitPositionsParams.builder().lang(gu).build()), true),
                new Case("transit.tarabala", "/v1/transit/tarabala",
                        c -> c.transit().tarabala(TransitTarabalaParams.builder().birth(ARJUN).date(D).time("06:00").tz("Asia/Kolkata").lang(gu).build()), true),
                new Case("milan.ashtakoota", "/v1/milan/ashtakoota",
                        c -> c.milan().ashtakoota(MilanAshtakootaParams.builder().boy(ARJUN).girl(GIRL).lang(gu).build()), true),
                new Case("muhurta.vivah", "/v1/muhurta/vivah",
                        c -> c.muhurta().vivah(MuhurtaVivahParams.builder().place(MUMBAI).date(D).lang(gu).build()), true),
                new Case("muhurta.bestTime", "/v1/muhurta/best-time",
                        c -> c.muhurta().bestTime(MuhurtaBestTimeParams.builder().place(MUMBAI).startDate(D).endDate("2027-01-17").lang(gu).build()), true),
                new Case("chart.planets", "/v1/chart/planets",
                        c -> c.chart().planets(ChartPlanetsParams.builder().birth(ARJUN).lang(gu).build()), true),
                new Case("chart.kundliFacts", "/v1/chart/kundli-facts",
                        c -> c.chart().kundliFacts(ChartKundliFactsParams.builder().birth(ARJUN).lang(gu).build()), true),
                new Case("prashna.chart", "/v1/prashna/chart",
                        c -> c.prashna().chart(PrashnaChartParams.builder().place(MUMBAI).date(D).time("12:00").lang(gu).build()), true),
                new Case("numerology.driver", "/v1/numerology/driver",
                        c -> c.numerology().driver(NumerologyDriverParams.builder().dob("1990-01-15").lang(gu).build()), true),
                new Case("numerology.full", "/v1/numerology/full",
                        c -> c.numerology().full(NumerologyFullParams.builder().name("Arjun Mehta").dob("1990-01-15").lang(gu).build()), false),
                new Case("dasha.vimshottari", "/v1/dasha/vimshottari",
                        c -> c.dasha().vimshottari(DashaVimshottariParams.builder().birth(ARJUN).lang(gu).build()), true),
                new Case("varshaphal.chart", "/v1/varshaphal/chart",
                        c -> c.varshaphal().chart(VarshaphalChartParams.builder().birth(ARJUN).year(2026).lang(gu).build()), true),
                new Case("ashtakavarga.bhinna", "/v1/ashtakavarga/bhinna/Jupiter",
                        c -> c.ashtakavarga().bhinna(AshtakavargaBhinnaParams.builder().birth(ARJUN).planet("Jupiter").lang(gu).build()), true),
                new Case("lalkitab.chart", "/v1/lalkitab/chart",
                        c -> c.lalkitab().chart(LalkitabChartParams.builder().birth(ARJUN).lang(gu).build()), true),
                new Case("horoscope.daily", "/v1/horoscope/daily",
                        c -> c.horoscope().daily(HoroscopeDailyParams.builder().rashi("aries").lang(gu).build()), false),
                new Case("reports.kundliLite", "/v1/reports/kundli/lite",
                        c -> c.reports().kundliLite(ReportsKundliLiteParams.builder().birth(ARJUN).lang(gu).build()), true),
                new Case("narrative.yearlyBhavishyafal", "/v1/vedic/narrative/yearly-bhavishyafal",
                        c -> c.narrative().yearlyBhavishyafal(NarrativeYearlyBhavishyafalParams.builder().birth(ARJUN).year(2027).lang(gu).build()), true),
                new Case("remedies.forGraha", "/v1/vedic/remedies/for-graha",
                        c -> c.remedies().forGraha(RemediesForGrahaParams.builder().graha("saturn").lang(gu).build()), true));
        for (Case k : cases) {
            StubServer.Recorded r = call(k.call());
            assertEquals(k.path(), r.path(), k.name());
            assertEquals("gu", r.query().get("lang"), k.name());
            if (k.both()) {
                assertEquals("gu", r.query().get("locale"), k.name());
            }
        }
    }

    @Test
    void acceptsEveryLangValueIncludingPa() {
        List<String> sent = new ArrayList<>();
        for (Lang lang : Lang.values()) {
            sent.add(call(c -> c.panchang().tithi(PanchangTithiParams.builder().place(MUMBAI).date(D).lang(lang).build()))
                    .query().get("locale"));
        }
        assertEquals(List.of("en", "hi", "mr", "gu", "bn", "te", "ta", "kn", "ml", "pa"), sent);
    }

    @Test
    void sendsNoLanguageParamWhenLangIsOmitted() {
        Map<String, String> q = call(c -> c.panchang().basic(PanchangBasicParams.builder().place(MUMBAI).date(D).build())).query();
        assertFalse(q.containsKey("lang"));
        assertFalse(q.containsKey("locale"));
    }

    @Test
    void keepsTheLocaleInputWorkingAndLangWinsWhenBothAreGiven() {
        Map<String, String> a = call(c -> c.festivals().month(FestivalsMonthParams.builder().year(2027).month(1).locale(Lang.HI).build())).query();
        assertEquals("hi", a.get("locale"));
        assertFalse(a.containsKey("lang"));
        Map<String, String> b = call(c -> c.panchang().monthly(PanchangMonthlyParams.builder()
                .lat(19.076).lon(72.8777).year(2027).month(1).locale(Lang.HI).lang(Lang.GU).build())).query();
        assertEquals("gu", b.get("locale"));
        assertEquals("gu", b.get("lang"));
    }

    /**
     * English-only products and Vedic endpoints with nothing to localize take no {@code lang}: their
     * builders have no such setter (a compile error rather than a dropped value, as in Node's types).
     */
    @Test
    void englishOnlyAndNumberOnlyMethodsHaveNoLangSetter() {
        for (Class<?> params : List.of(WesternNatalPlanetsParams.class, WesternSynastryAspectsParams.class,
                WesternTransitsPositionsParams.class, TarotDailyParams.class, GeoSearchParams.class,
                PanchangRahuKaalParams.class, ChartYogasParams.class, TransitWindowsParams.class)) {
            Class<?> builder = Arrays.stream(params.getClasses()).filter(c -> c.getSimpleName().equals("Builder"))
                    .findFirst().orElseThrow();
            assertTrue(Arrays.stream(builder.getMethods()).noneMatch(m -> m.getName().equals("lang") || m.getName().equals("locale")),
                    params.getSimpleName());
        }
    }

    @Test
    void westernNarrativeKeepsItsOwnLang() {
        assertEquals("hi", call(c -> c.western().narrative().sabian(WesternNarrativeSabianParams.builder()
                .degree(100).lang(Lang.HI).build())).query().get("lang"));
    }

    @Test
    void langCodes() {
        assertEquals(Lang.GU, Lang.fromCode("gu"));
        assertEquals(Lang.BN, Lang.fromCode(" BN "));
        assertEquals("pa", Lang.PA.code());
        assertFalse(Lang.PA.isComplete());
        assertTrue(Lang.HI.isComplete());
        assertThrows(IllegalArgumentException.class, () -> Lang.fromCode("xx"));
        assertEquals(HouseSystem.WHOLE_SIGN, HouseSystem.fromValue("WHOLE_SIGN"));
        assertEquals(Ayanamsa.TRUE_CHITRAPAKSHA, Ayanamsa.fromValue("true_chitrapaksha"));
    }
}
