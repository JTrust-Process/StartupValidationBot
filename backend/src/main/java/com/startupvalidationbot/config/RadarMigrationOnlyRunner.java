package com.startupvalidationbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "radar.migrate-only", havingValue = "true")
public class RadarMigrationOnlyRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RadarMigrationOnlyRunner.class);
    private final ConfigurableApplicationContext context;

    public RadarMigrationOnlyRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migrations and schema validation completed; exiting migration release process");
        int status = SpringApplication.exit(context, () -> 0);
        System.exit(status);
    }
}
