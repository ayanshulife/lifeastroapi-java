package com.lifeastroapi.spring.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeastroapi.Lang;
import com.lifeastroapi.LifeAstro;
import com.lifeastroapi.Place;
import com.lifeastroapi.params.PanchangBasicParams;
import com.lifeastroapi.spring.TestApi;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The README's Hibernate / JPA section, run for real: a Spring Boot application with H2, the
 * auto-configured {@link LifeAstro} bean and a local stand-in for the API.
 */
@SpringBootTest(classes = JpaApp.class)
class JpaExampleTest {

    static final TestApi API = new TestApi();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lifeastroapi.api-key", () -> "dv_test_jpa");
        registry.add("lifeastroapi.base-url", API::baseUrl);
        registry.add("lifeastroapi.max-retries", () -> "0");
    }

    @AfterAll
    static void stop() {
        API.close();
    }

    @Autowired
    CustomerRepository customers;

    @Autowired
    SavedReportRepository reports;

    @Autowired
    AstroService service;

    @Test
    void entityToBirthInputCachedPanchangAndAStoredReport() throws Exception {
        Customer arjun = customers.save(new Customer("Arjun Mehta", LocalDate.of(1990, 1, 15), LocalTime.of(10, 30),
                "Asia/Kolkata", 19.076, 72.8777));

        // 1. The entity's columns become the chart input.
        service.birthChart(arjun.getId(), Lang.HI);
        TestApi.Request chart = API.requests("/v1/chart/planets").get(0);
        assertThat(chart.query()).containsAllEntriesOf(Map.of(
                "date", "1990-01-15", "time", "10:30", "tz", "Asia/Kolkata",
                "lat", "19.076", "lon", "72.8777", "lang", "hi"));
        assertThat(chart.authorization()).isEqualTo("Bearer dv_test_jpa");

        // 2. Today's panchang for a city is shared by every customer there: one API call.
        PanchangBasicParams mumbaiToday = PanchangBasicParams.builder()
                .place(Place.of(19.076, 72.8777, "Asia/Kolkata")).date(LocalDate.of(2027, 1, 15)).lang(Lang.GU).build();
        JsonNode first = service.panchang(mumbaiToday);
        JsonNode second = service.panchang(PanchangBasicParams.builder()
                .place(Place.of(19.076, 72.8777, "Asia/Kolkata")).date(LocalDate.of(2027, 1, 15)).lang(Lang.GU).build());
        assertThat(second).isEqualTo(first);
        assertThat(API.requests("/v1/panchang/basic")).hasSize(1);

        // 3. A report's JSON is kept in a @Lob column and read back without another call.
        API.answer(r -> "{\"report\":\"kundli-lite\",\"lang\":\"" + r.query().get("lang") + "\",\"name\":\""
                + r.query().get("name") + "\"}");
        Long reportId = service.saveKundliReport(arjun.getId(), Lang.GU).getId();
        SavedReport stored = reports.findById(reportId).orElseThrow();
        JsonNode json = new ObjectMapper().readTree(stored.getJson());
        assertThat(json.path("lang").asText()).isEqualTo("gu");
        assertThat(json.path("name").asText()).isEqualTo("Arjun Mehta");
        assertThat(stored.getKind()).isEqualTo("kundli-lite");
        assertThat(API.requests("/v1/reports/kundli/lite")).hasSize(1);
    }
}
