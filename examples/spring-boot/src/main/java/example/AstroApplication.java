package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** A Spring Boot application with JPA (H2 here) and the auto-configured LifeAstro bean. */
@SpringBootApplication
@EnableCaching
public class AstroApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstroApplication.class, args);
    }
}
