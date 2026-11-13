package com.example.taskmanager;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Used by the Helm migration Job (threat #18): Flyway's own migration already ran during context
 * startup (FlywayMigrationInitializer, before any ApplicationRunner), so by the time this runs
 * there's nothing left to do except make the container exit 0 -- the signal a Kubernetes Job needs
 * to mark itself Succeeded.
 */
@Component
@Profile("migrate")
public class MigrationExitRunner implements ApplicationRunner {

    private final ApplicationContext context;

    public MigrationExitRunner(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
