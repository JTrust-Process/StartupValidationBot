package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessorsFactory;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

import com.startupvalidationbot.BackendApplication;
import com.startupvalidationbot.config.DatabaseUrlEnvironmentPostProcessor;

class DatabaseUrlEnvironmentPostProcessorTest {
    @Test
    void isRegisteredWithSpringBootFactoryLoader() {
        assertThat(EnvironmentPostProcessorsFactory
                .fromSpringFactories(DatabaseUrlEnvironmentPostProcessorTest.class.getClassLoader())
                .getEnvironmentPostProcessors(new DeferredLogs(), new DefaultBootstrapContext()))
                .anyMatch(DatabaseUrlEnvironmentPostProcessor.class::isInstance);
    }

    @Test
    void convertsFlyStylePostgresUrlToJdbcProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://radar:p%40ss@db.internal:5433/startups?sslmode=require");

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment,
                new SpringApplication(BackendApplication.class));

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:5433/startups?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("radar");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss");
    }
}
