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
    void appliesAllProductionMigrationsAndRequeuesLegacyScanningTasks() throws Exception {
        Flyway baseline = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load();

        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(2);
        try (Connection connection = postgres.createConnection("")) {
            connection.createStatement().executeUpdate("""
                    INSERT INTO import_batches (id, status, domain, created_by, created_at, updated_at)
                    VALUES ('legacy-scan', 'PROCESSING', 'default', 'migration-test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            connection.createStatement().executeUpdate("""
                    INSERT INTO import_file_tasks (
                        batch_id, original_name, storage_key, content_hash, size_bytes, status, created_at, updated_at
                    ) VALUES (
                        'legacy-scan', 'legacy.pdf', 'legacy.pdf', 'legacy-hash', 1, 'SCANNING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
        }

        Flyway upgrade = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);

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
        try (Connection connection = postgres.createConnection("");
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT status FROM import_file_tasks WHERE batch_id = 'legacy-scan'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("QUEUED");
        }
        try (Connection connection = postgres.createConnection("");
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT COUNT(*)
                     FROM information_schema.columns
                     WHERE table_name = 'knowledge_documents'
                       AND column_name = 'import_task_id'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
        try (Connection connection = postgres.createConnection("");
             ResultSet result = connection.createStatement().executeQuery("""
                     SELECT COUNT(*)
                     FROM pg_indexes
                     WHERE tablename = 'knowledge_documents'
                       AND indexname = 'uk_knowledge_documents_import_task'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
