package com.diseaseprediction;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real PostgreSQL (same major version as compose.yaml) for tests that need a database.
 * {@code @ServiceConnection} points the datasource at it, so Flyway runs the real migrations.
 * Requires a running Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
