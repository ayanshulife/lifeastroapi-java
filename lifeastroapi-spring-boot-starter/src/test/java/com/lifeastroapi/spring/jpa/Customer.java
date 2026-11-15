package com.lifeastroapi.spring.jpa;

import com.lifeastroapi.BirthInput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** A customer of the application, with the birth details the API needs. The SDK never sees the database. */
@Entity
public class Customer {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private LocalTime birthTime;

    /** IANA zone of the birth place, e.g. {@code Asia/Kolkata}. */
    @Column(nullable = false)
    private String birthTimezone;

    private double birthLat;

    private double birthLon;

    protected Customer() {
    }

    public Customer(String name, LocalDate birthDate, LocalTime birthTime, String birthTimezone, double birthLat,
            double birthLon) {
        this.name = name;
        this.birthDate = birthDate;
        this.birthTime = birthTime;
        this.birthTimezone = birthTimezone;
        this.birthLat = birthLat;
        this.birthLon = birthLon;
    }

    /** The SDK's chart input for this customer. */
    public BirthInput toBirthInput() {
        return BirthInput.of(birthDate, birthTime, ZoneId.of(birthTimezone), birthLat, birthLon);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
