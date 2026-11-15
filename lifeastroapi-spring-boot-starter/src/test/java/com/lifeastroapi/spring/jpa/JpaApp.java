package com.lifeastroapi.spring.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** The test application: JPA on an in-memory H2 database, caching on, the LifeAstro bean auto-configured. */
@SpringBootApplication
@EnableCaching
public class JpaApp {
}
