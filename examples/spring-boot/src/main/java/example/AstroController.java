package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeastroapi.Lang;
import com.lifeastroapi.Place;
import com.lifeastroapi.params.PanchangBasicParams;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * <pre>
 * curl -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
 *   -d '{"name":"Arjun Mehta","birthDate":"1990-01-15","birthTime":"10:30","birthTimezone":"Asia/Kolkata","birthLat":19.076,"birthLon":72.8777}'
 * curl 'localhost:8081/api/customers/1/chart?lang=hi'
 * curl 'localhost:8081/api/panchang/today?city=mumbai&lang=gu'
 * curl -X POST 'localhost:8081/api/customers/1/reports/kundli-lite?lang=gu'
 * curl localhost:8081/api/reports/1
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class AstroController {

    private static final Map<String, Place> CITIES = Map.of(
            "mumbai", Place.of(19.076, 72.8777, "Asia/Kolkata"),
            "delhi", Place.of(28.6139, 77.209, "Asia/Kolkata"),
            "kolkata", Place.of(22.5726, 88.3639, "Asia/Kolkata"),
            "chennai", Place.of(13.0827, 80.2707, "Asia/Kolkata"));

    private final AstroService astro;
    private final CustomerRepository customers;
    private final SavedReportRepository reports;
    private final ObjectMapper json;

    public AstroController(AstroService astro, CustomerRepository customers, SavedReportRepository reports,
            ObjectMapper json) {
        this.astro = astro;
        this.customers = customers;
        this.reports = reports;
        this.json = json;
    }

    /** What a client sends to register a customer. */
    public record NewCustomer(String name, LocalDate birthDate, LocalTime birthTime, String birthTimezone,
            double birthLat, double birthLon) {
    }

    @PostMapping("/customers")
    public Map<String, Object> addCustomer(@RequestBody NewCustomer c) {
        Customer saved = customers.save(new Customer(c.name(), c.birthDate(), c.birthTime(), c.birthTimezone(),
                c.birthLat(), c.birthLon()));
        return Map.of("id", saved.getId());
    }

    @GetMapping("/customers/{id}/chart")
    public JsonNode chart(@PathVariable long id, @RequestParam(defaultValue = "en") String lang) {
        return astro.birthChart(id, Lang.fromCode(lang));
    }

    @GetMapping("/panchang/today")
    public JsonNode today(@RequestParam String city, @RequestParam(defaultValue = "en") String lang) {
        Place place = CITIES.get(city.toLowerCase());
        if (place == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown city " + city);
        }
        return astro.panchang(PanchangBasicParams.builder()
                .place(place)
                .date(LocalDate.now(ZoneId.of(place.tz())))
                .lang(Lang.fromCode(lang))
                .build());
    }

    @PostMapping("/customers/{id}/reports/kundli-lite")
    public Map<String, Object> saveReport(@PathVariable long id, @RequestParam(defaultValue = "en") String lang) {
        return Map.of("reportId", astro.saveKundliReport(id, Lang.fromCode(lang)).getId());
    }

    @GetMapping("/reports/{id}")
    public JsonNode report(@PathVariable long id) throws Exception {
        SavedReport r = reports.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return json.readTree(r.getJson());
    }
}
