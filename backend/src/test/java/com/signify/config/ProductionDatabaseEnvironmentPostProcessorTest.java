package com.signify.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProductionDatabaseEnvironmentPostProcessorTest {

    private final ProductionDatabaseEnvironmentPostProcessor guard = new ProductionDatabaseEnvironmentPostProcessor();

    private Map<String, Object> settings() {
        return new HashMap<>(Map.of("SIGNIFY_DB_URL", "jdbc:postgresql://ep-test.ap-southeast-1.aws.neon.tech/neondb?sslmode=require",
                "SIGNIFY_DB_USERNAME", "test-role", "SIGNIFY_DB_PASSWORD", "sentinel-password-not-for-logs"));
    }

    private ConfigurableEnvironment environment(boolean production, Map<String, Object> runtime) throws IOException {
        var env = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        env.getPropertySources().addLast(loader.load("base", new ClassPathResource("application.yml")).get(0));
        if (production) {
            env.setActiveProfiles("production");
            env.getPropertySources().addFirst(loader.load("production", new ClassPathResource("application-production.yml")).get(0));
        }
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("runtime", runtime));
        return env;
    }

    private void check(ConfigurableEnvironment env) {
        guard.postProcessEnvironment(env, new SpringApplication());
    }

    @Test
    void preservesLocalDefaultsWithoutProductionValidation() throws Exception {
        var env = environment(false, Map.of());
        check(env);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/signify");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("postgres");
        assertThat(env.getProperty("spring.datasource.password")).isEmpty();
    }

    @Test
    void runtimeSettingsResolveProductionYamlAndKeepFlywayOnTheSharedDatasource() throws Exception {
        var runtime = settings();
        var env = environment(true, runtime);
        check(env);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(runtime.get("SIGNIFY_DB_URL"));
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo(runtime.get("SIGNIFY_DB_USERNAME"));
        assertThat(env.getProperty("spring.flyway.url")).isNull();
        assertThat(env.getProperty("spring.flyway.user")).isNull();
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIGNIFY_DB_URL", "SIGNIFY_DB_USERNAME", "SIGNIFY_DB_PASSWORD"})
    void missingAndBlankProductionSettingsFailWithoutRevealingCredentials(String key) throws Exception {
        for (Object value : new Object[] {null, " "}) {
            var runtime = settings();
            if (value == null) runtime.remove(key); else runtime.put(key, value);
            var env = environment(true, runtime);
            assertThatThrownBy(() -> check(env)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key + " is required").hasMessageNotContaining("sentinel-password-not-for-logs");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "localhost.", "127.0.0.1", "0.0.0.0", "[::1]", "[0:0:0:0:0:0:0:1]"})
    void productionRejectsLoopbackInsteadOfOpeningAConnection(String host) throws Exception {
        var runtime = settings();
        runtime.put("SIGNIFY_DB_URL", "jdbc:postgresql://" + host + ":5432/signify");
        var env = environment(true, runtime);
        assertThatThrownBy(() -> check(env)).hasMessageContaining("local/loopback host");
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgresql://example.com/neondb", "jdbc:postgresql://example.com", "jdbc:postgresql://127.1/signify",
            "jdbc:postgresql://user:sentinel-password-not-for-logs@example.com/neondb",
            "jdbc:postgresql://example.com/neondb?password=sentinel-password-not-for-logs",
            "jdbc:postgresql://example.com/neondb?%70assword=sentinel-password-not-for-logs"})
    void malformedOrCredentialBearingUrlsFailWithSanitizedErrors(String url) throws Exception {
        var runtime = settings();
        runtime.put("SIGNIFY_DB_URL", url);
        var env = environment(true, runtime);
        assertThatThrownBy(() -> check(env)).hasMessageContaining("password-free jdbc:postgresql://host/database")
                .hasMessageNotContaining("sentinel-password-not-for-logs").hasNoCause();
    }

    @Test
    void detectsStandardSpringOverrideRatherThanTrustingTheCanonicalVariableAlone() throws Exception {
        var runtime = settings();
        runtime.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/signify");
        var env = environment(true, runtime);
        assertThat(env.getProperty("spring.datasource.url")).contains("localhost");
        assertThatThrownBy(() -> check(env)).hasMessageContaining("conflicts with SIGNIFY_DB_URL");
        runtime.put("SPRING_DATASOURCE_URL", runtime.get("SIGNIFY_DB_URL"));
        assertThatCode(() -> check(environment(true, runtime))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_DATASOURCE_HIKARI_JDBC_URL", "SPRING_FLYWAY_URL", "SPRING_FLYWAY_USER", "SPRING_FLYWAY_PASSWORD"})
    void rejectsSeparateHikariAndFlywayConnectionOverrides(String key) throws Exception {
        var runtime = settings();
        runtime.put(key, "sentinel-password-not-for-logs");
        var env = environment(true, runtime);
        assertThatThrownBy(() -> check(env)).hasMessageContaining("override").hasMessageNotContaining("sentinel-password-not-for-logs");
    }

    @Test
    void renderCannotLoseTheProductionProfileAndFallBackToLocalDefaults() throws Exception {
        var env = environment(false, Map.of("RENDER", "true"));
        assertThatThrownBy(() -> check(env)).hasMessageContaining("SPRING_PROFILES_ACTIVE=production");
    }

    @Test
    void neonRequiresTlsAndTheDirectHost() throws Exception {
        var runtime = settings();
        runtime.put("SIGNIFY_DB_URL", "jdbc:postgresql://ep-test.ap-southeast-1.aws.neon.tech/neondb");
        assertThatThrownBy(() -> check(environment(true, runtime))).hasMessageContaining("Neon requires TLS");
        runtime.put("SIGNIFY_DB_URL", "jdbc:postgresql://ep-test-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require");
        assertThatThrownBy(() -> check(environment(true, runtime))).hasMessageContaining("direct/unpooled");
    }
}
