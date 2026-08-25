package com.startupvalidationbot.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL", "").trim();
        if (databaseUrl.isEmpty()) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        if (databaseUrl.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", databaseUrl);
        } else if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            URI uri = URI.create(databaseUrl);
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            properties.put("spring.datasource.url",
                    "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);

            String userInfo = uri.getRawUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] credentials = userInfo.split(":", 2);
                properties.put("spring.datasource.username", decode(credentials[0]));
                if (credentials.length == 2) {
                    properties.put("spring.datasource.password", decode(credentials[1]));
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "DATABASE_URL must be a jdbc:postgresql:, postgres://, or postgresql:// URL");
        }

        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", properties));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
