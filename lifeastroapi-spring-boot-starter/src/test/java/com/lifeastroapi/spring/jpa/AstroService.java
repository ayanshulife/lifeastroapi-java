package com.lifeastroapi.spring.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeastroapi.Lang;
import com.lifeastroapi.LifeAstro;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.PanchangBasicParams;
import com.lifeastroapi.params.ReportsKundliLiteParams;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The README's Hibernate / JPA example: entity to {@code BirthInput}, a shared cache, a stored report. */
@Service
public class AstroService {

    private final LifeAstro astro;
    private final CustomerRepository customers;
    private final SavedReportRepository reports;

    public AstroService(LifeAstro astro, CustomerRepository customers, SavedReportRepository reports) {
        this.astro = astro;
        this.customers = customers;
        this.reports = reports;
    }

    /** A customer's birth chart, from the birth details in the database. */
    @Transactional(readOnly = true)
    public JsonNode birthChart(long customerId, Lang lang) {
        Customer c = customers.findById(customerId).orElseThrow();
        return astro.chart().planets(ChartPlanetsParams.builder().birth(c.toBirthInput()).lang(lang).build());
    }

    /**
     * The same for every customer of a city, so it is cached. Parameter objects have value equality,
     * so they are the cache key; give them an explicit {@code date} so the key changes every day.
     */
    @Cacheable("panchang")
    public JsonNode panchang(PanchangBasicParams params) {
        return astro.panchang().basic(params);
    }

    /** Fetches a report once and keeps its JSON. */
    @Transactional
    public SavedReport saveKundliReport(long customerId, Lang lang) {
        Customer c = customers.findById(customerId).orElseThrow();
        JsonNode report = astro.reports().kundliLite(ReportsKundliLiteParams.builder()
                .birth(c.toBirthInput()).name(c.getName()).lang(lang).build());
        return reports.save(new SavedReport(c, "kundli-lite", lang.code(), report.toString()));
    }
}
