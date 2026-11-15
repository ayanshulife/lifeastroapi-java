package com.lifeastroapi.spring.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/**
 * A report's JSON kept for later (a {@code @Lob} here; {@code jsonb} on PostgreSQL), so showing it
 * again costs no credits.
 */
@Entity
public class SavedReport {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String lang;

    @Lob
    @Column(nullable = false)
    private String json;

    @Column(nullable = false)
    private Instant createdAt;

    protected SavedReport() {
    }

    public SavedReport(Customer customer, String kind, String lang, String json) {
        this.customer = customer;
        this.kind = kind;
        this.lang = lang;
        this.json = json;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getLang() {
        return lang;
    }

    public String getJson() {
        return json;
    }
}
