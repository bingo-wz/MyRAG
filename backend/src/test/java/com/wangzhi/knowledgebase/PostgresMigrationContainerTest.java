package com.wangzhi.knowledgebase;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationContainerTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("myrag")
            .withUsername("myrag")
            .withPassword("myrag-test");

    @Test
    void appliesAllProductionMigrations() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        try (Connection connection = postgres.createConnection("");
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT COUNT(*)
                     FROM information_schema.columns
                     WHERE table_name = 'question_logs'
                       AND column_name IN ('model_name', 'prompt_version', 'fallback')
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }
}
