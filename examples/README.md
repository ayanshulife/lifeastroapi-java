# Examples

Both examples use the published artifacts (`com.lifeastroapi:*:0.1.0`). To run them against your
checkout instead, first install it locally: `./mvnw install` in the repository root.

## [quickstart](quickstart/)

Plain Java: today's panchang in Gujarati, a birth chart, kundli matching in Hindi and this month's
festivals in Bengali, with the credits left after each call.

```bash
cd examples/quickstart
export LIFEASTRO_API_KEY=dv_live_...
mvn -q compile exec:java
```

## [spring-boot](spring-boot/)

A Spring Boot 3 application with JPA (H2 here) and the starter: customers with their birth details
in an `@Entity`, the chart from those columns, today's panchang per city cached with `@Cacheable`,
and a report's JSON kept in a `@Lob` column.

```bash
cd examples/spring-boot
export LIFEASTRO_API_KEY=dv_live_...
mvn spring-boot:run
```

Then call it as shown in `AstroController`:

```bash
curl -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
  -d '{"name":"Arjun Mehta","birthDate":"1990-01-15","birthTime":"10:30","birthTimezone":"Asia/Kolkata","birthLat":19.076,"birthLon":72.8777}'
curl 'localhost:8081/api/customers/1/chart?lang=hi'
curl 'localhost:8081/api/panchang/today?city=mumbai&lang=gu'
curl -X POST 'localhost:8081/api/customers/1/reports/kundli-lite?lang=gu'
curl localhost:8081/api/reports/1
```
