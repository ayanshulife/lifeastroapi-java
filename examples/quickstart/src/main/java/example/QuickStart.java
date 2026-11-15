package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeastroapi.BirthInput;
import com.lifeastroapi.Lang;
import com.lifeastroapi.LifeAstro;
import com.lifeastroapi.Place;
import com.lifeastroapi.errors.LifeAstroException;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.FestivalsMonthParams;
import com.lifeastroapi.params.MilanAshtakootaParams;
import com.lifeastroapi.params.PanchangBasicParams;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Today's panchang in Gujarati, a birth chart, kundli matching and this month's festivals in
 * Bengali.
 *
 * <pre>
 * export LIFEASTRO_API_KEY=dv_live_...
 * mvn -q compile exec:java
 * # against a local server: mvn -q compile exec:java -Dexec.args=http://localhost:8080
 * </pre>
 */
public final class QuickStart {

    public static void main(String[] args) {
        LifeAstro.Builder builder = LifeAstro.builder() // the key comes from LIFEASTRO_API_KEY
                .onResponse(info -> System.out.printf("  [%s %d, request %s, credits left %s]%n",
                        info.path(), info.status(), info.requestId(), info.creditsRemaining()));
        if (args.length > 0) {
            builder.baseUrl(args[0]);
        }

        try (LifeAstro client = builder.build()) {
            // 1. Today's panchang in Mumbai, in Gujarati
            Place mumbai = Place.of(19.076, 72.8777, "Asia/Kolkata");
            JsonNode day = client.panchang().basic(PanchangBasicParams.builder()
                    .place(mumbai)
                    .date(LocalDate.now(ZoneId.of("Asia/Kolkata")))
                    .lang(Lang.GU)
                    .build());
            System.out.println("Tithi: " + day.path("tithi").path("name").asText()
                    + ", nakshatra: " + day.path("nakshatra").path("name").asText());

            // 2. A Vedic birth chart: Arjun Mehta, 1990-01-15 10:30, Mumbai
            BirthInput arjun = BirthInput.of("1990-01-15", "10:30", "Asia/Kolkata", 19.076, 72.8777);
            JsonNode planets = client.chart().planets(ChartPlanetsParams.builder().birth(arjun).build());
            for (JsonNode p : planets.path("planets")) {
                System.out.printf("%-8s house %2d  %s%n", p.path("name").asText(), p.path("house").asInt(),
                        p.path("dms_in_sign").asText());
            }

            // 3. Kundli matching, the Ashtakoota score out of 36, in Hindi
            JsonNode match = client.milan().ashtakoota(MilanAshtakootaParams.builder()
                    .boy(arjun)
                    .girl(BirthInput.of("1992-06-20", "14:15", "Asia/Kolkata", 28.6139, 77.209))
                    .lang(Lang.HI)
                    .build());
            System.out.println("Ashtakoota: " + match.path("total").asDouble() + " / " + match.path("max").asInt());

            // 4. This month's festivals in Kolkata, in Bengali
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            JsonNode festivals = client.festivals().month(FestivalsMonthParams.builder()
                    .year(today.getYear()).month(today.getMonthValue())
                    .place(Place.of(22.5726, 88.3639, "Asia/Kolkata"))
                    .lang(Lang.BN)
                    .build());
            for (JsonNode f : festivals.path("festivals")) {
                System.out.println(f.path("date").asText() + "  " + f.path("name").asText());
            }
        } catch (LifeAstroException e) {
            System.err.println("LifeAstroAPI error: " + e);
            System.exit(1);
        }
    }
}
