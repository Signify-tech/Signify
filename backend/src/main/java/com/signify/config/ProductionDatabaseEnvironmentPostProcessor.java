package com.signify.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** Checks resolved configuration before datasource/Flyway beans can open a connection. */
public final class ProductionDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean production = environment.acceptsProfiles(Profiles.of("production"));
        boolean render = "true".equalsIgnoreCase(read(environment, "RENDER"));
        if (render && !production) {
            throw failure("Render requires SPRING_PROFILES_ACTIVE=production. Check the deployed revision and profile overrides.");
        }
        if (!production) return;

        String url = required(environment, "SIGNIFY_DB_URL");
        required(environment, "SIGNIFY_DB_USERNAME");
        required(environment, "SIGNIFY_DB_PASSWORD");
        validateUrl(url);

        // Standard Spring env, JSON, JVM options and CLI args have higher precedence than YAML.
        for (String field : List.of("url", "username", "password")) {
            String canonical = "SIGNIFY_DB_" + field.toUpperCase(Locale.ROOT);
            String effective = read(environment, "spring.datasource." + field);
            if (!required(environment, canonical).equals(effective)) {
                throw failure("spring.datasource." + field + " conflicts with " + canonical
                        + ". Remove SPRING_DATASOURCE_* or other datasource overrides; use SIGNIFY_DB_* consistently.");
            }
        }
        for (String field : List.of("jdbc-url", "username", "password")) {
            String hikari = read(environment, "spring.datasource.hikari." + field);
            String effective = read(environment, "spring.datasource." + (field.equals("jdbc-url") ? "url" : field));
            if (hikari != null && !hikari.equals(effective)) {
                throw failure("Conflicting spring.datasource.hikari." + field + " override. Remove it.");
            }
        }
        for (String field : List.of("url", "user", "password")) {
            if (read(environment, "spring.flyway." + field) != null) {
                throw failure("Remove spring.flyway." + field
                        + " / SPRING_FLYWAY_* connection overrides. Flyway must use the application datasource.");
            }
        }
        if ("false".equalsIgnoreCase(read(environment, "spring.flyway.enabled"))) {
            throw failure("Flyway must remain enabled in production.");
        }
    }

    private static void validateUrl(String url) {
        URI uri;
        Map<String, String> query = new HashMap<>();
        try {
            if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            uri = URI.create(url.substring("jdbc:".length()));
            if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2
                    || uri.getFragment() != null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException();
            if (uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    String[] pair = part.split("=", 2);
                    String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                    if (List.of("user", "username", "password", "sslpassword").contains(key)
                            || query.put(key, value) != null) throw new IllegalArgumentException();
                }
            }
        } catch (IllegalArgumentException e) {
            // Never retain the parsing exception: its message can contain credentials from the input.
            throw failure("SIGNIFY_DB_URL must be a password-free jdbc:postgresql://host/database URL. "
                    + "Keep credentials in SIGNIFY_DB_USERNAME and SIGNIFY_DB_PASSWORD.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        if (host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.")
                || host.equals("0.0.0.0") || host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]")) {
            throw failure("SIGNIFY_DB_URL targets a local/loopback host. Production requires an external PostgreSQL host.");
        }
        if (host.endsWith(".neon.tech")) {
            if (host.contains("-pooler.")) {
                throw failure("Use the direct/unpooled Neon host for the shared datasource and Flyway.");
            }
            if (!List.of("require", "verify-ca", "verify-full").contains(query.getOrDefault("sslmode", ""))) {
                throw failure("Neon requires TLS: add sslmode=require or sslmode=verify-full to SIGNIFY_DB_URL.");
            }
        }
    }

    private static String required(ConfigurableEnvironment environment, String key) {
        String value = read(environment, key);
        if (value == null || value.isBlank()) {
            throw failure(key + " is required and must be nonblank in production. Set it in the service runtime environment.");
        }
        return value;
    }

    private static String read(ConfigurableEnvironment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException e) {
            throw failure("Unresolved configuration for " + key + ". Check the required runtime environment variables.");
        }
    }

    private static IllegalStateException failure(String message) {
        return new IllegalStateException("Signify production database configuration: " + message);
    }
}
