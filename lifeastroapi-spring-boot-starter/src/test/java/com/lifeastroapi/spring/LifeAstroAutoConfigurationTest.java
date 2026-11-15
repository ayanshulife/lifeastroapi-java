package com.lifeastroapi.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.lifeastroapi.LifeAstro;
import com.lifeastroapi.ResponseInfo;
import com.lifeastroapi.params.PanchangTithiParams;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

class LifeAstroAutoConfigurationTest {

    private final TestApi api = new TestApi();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LifeAstroAutoConfiguration.class));

    @AfterEach
    void stop() {
        api.close();
    }

    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("lifeastroapi.api-key=dv_test_spring", "lifeastroapi.base-url=" + api.baseUrl());
    }

    @Test
    void createsTheBeanFromProperties() {
        configured().withPropertyValues("lifeastroapi.timeout=5s", "lifeastroapi.max-retries=0").run(context -> {
            assertThat(context).hasSingleBean(LifeAstro.class);
            LifeAstro client = context.getBean(LifeAstro.class);
            assertThat(client.baseUrl()).isEqualTo(api.baseUrl());
            assertThat(client.timeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(client.maxRetries()).isZero();
            client.panchang().tithi(PanchangTithiParams.builder().lat(19.076).lon(72.8777).tz("Asia/Kolkata").date("2027-01-15").build());
            assertThat(api.requests()).singleElement().satisfies(r -> {
                assertThat(r.path()).isEqualTo("/v1/panchang/tithi");
                assertThat(r.authorization()).isEqualTo("Bearer dv_test_spring");
                assertThat(r.query()).containsEntry("lat", "19.076");
            });
            assertThat(context.getBean(LifeAstroProperties.class).toString()).doesNotContain("dv_test_spring");
        });
    }

    @Test
    void defaultsMatchTheSdk() {
        runner.withPropertyValues("lifeastroapi.api-key=dv_test_spring").run(context -> {
            LifeAstro client = context.getBean(LifeAstro.class);
            assertThat(client.baseUrl()).isEqualTo(LifeAstro.DEFAULT_BASE_URL);
            assertThat(client.timeout()).isEqualTo(LifeAstro.DEFAULT_TIMEOUT);
            assertThat(client.maxRetries()).isEqualTo(LifeAstro.DEFAULT_MAX_RETRIES);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClient {
        @Bean
        LifeAstro myClient() {
            return LifeAstro.builder().apiKey("dv_test_own").baseUrl("http://127.0.0.1:9").build();
        }
    }

    @Test
    void backsOffWhenTheApplicationDefinesItsOwnBean() {
        configured().withUserConfiguration(OwnClient.class).run(context -> {
            assertThat(context).hasSingleBean(LifeAstro.class);
            assertThat(context).hasBean("myClient");
            assertThat(context.getBean(LifeAstro.class).baseUrl()).isEqualTo("http://127.0.0.1:9");
        });
    }

    @Test
    void canBeTurnedOff() {
        configured().withPropertyValues("lifeastroapi.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(LifeAstro.class));
    }

    @Test
    void failsToStartWithoutAnApiKey() {
        assumeTrue(System.getenv("LIFEASTRO_API_KEY") == null, "LIFEASTRO_API_KEY is set in this environment");
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "LifeAstro: an API key is required. Call apiKey(...) or set LIFEASTRO_API_KEY. "
                            + "Get one at https://lifeastroapi.com/dashboard/keys");
        });
    }

    static final List<String> ORDER = new CopyOnWriteArrayList<>();
    static final List<ResponseInfo> SEEN = new CopyOnWriteArrayList<>();

    @Configuration(proxyBeanMethods = false)
    static class Customizers {
        @Bean
        @Order(2)
        LifeAstroBuilderCustomizer second() {
            return b -> ORDER.add("second");
        }

        @Bean
        @Order(1)
        LifeAstroBuilderCustomizer creditsLog() {
            return b -> {
                ORDER.add("first");
                b.onResponse(SEEN::add).defaultHeader("X-App", "test");
            };
        }
    }

    @Test
    void appliesCustomizersInOrder() {
        ORDER.clear();
        SEEN.clear();
        configured().withUserConfiguration(Customizers.class).run(context -> {
            context.getBean(LifeAstro.class).meta().version();
            assertThat(ORDER).containsExactly("first", "second");
            assertThat(SEEN).singleElement().satisfies(info -> {
                assertThat(info.creditsRemaining()).isEqualTo(64043L);
                assertThat(info.requestId()).isEqualTo("req_1");
            });
        });
    }

    /** A DTO in the application's naming style. */
    public static final class Tithi {
        public String nameEn;
    }

    @Configuration(proxyBeanMethods = false)
    static class SnakeCaseMapper {
        @Bean
        ObjectMapper objectMapper() {
            // Like Spring Boot's own mapper: unknown properties are ignored.
            return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
    }

    @Test
    void usesTheApplicationObjectMapper() {
        api.answer(r -> "{\"name_en\":\"Saptami\",\"index\":6}");
        configured().withUserConfiguration(SnakeCaseMapper.class).run(context -> {
            Tithi t = context.getBean(LifeAstro.class).request("/v1/panchang/tithi", null, Tithi.class);
            assertThat(t.nameEn).isEqualTo("Saptami");
        });
    }

    @Test
    void theContextClosesTheClient() {
        LifeAstro[] holder = new LifeAstro[1];
        configured().run(context -> holder[0] = context.getBean(LifeAstro.class));
        assertThatThrownBy(() -> holder[0].meta().version()).isInstanceOf(IllegalStateException.class);
    }
}
