package uk.gov.hmcts.cp.db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies V1.006's collision-guarded rule-id renumbering.
 *
 * <p>Runs Flyway directly (via the Java API) against a throwaway PostgreSQL container so that
 * each scenario can control exactly which migrations have applied before a stale row is injected
 * -- this cannot be exercised through {@code IntegrationTestBase}, whose shared container always
 * migrates the full, collision-free chain once at startup.
 */
@Testcontainers
@DisplayName("V1.006 renumber_rule_ids migration")
class RuleIdRenumberingMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.3")
                    .withDatabaseName("rule_id_renumbering_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private DataSource dataSource;

    @BeforeEach
    void resetSchema() throws Exception {
        PGSimpleDataSource simpleDataSource = new PGSimpleDataSource();
        simpleDataSource.setUrl(POSTGRES.getJdbcUrl());
        simpleDataSource.setUser(POSTGRES.getUsername());
        simpleDataSource.setPassword(POSTGRES.getPassword());
        dataSource = simpleDataSource;

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    private Flyway flywayTargeting(final String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private boolean ruleExists(final String id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM validation_rule WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Object[] ruleRow(final String id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT enabled, severity FROM validation_rule WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                final boolean hasRow = resultSet.next();
                assertThat(hasRow).as("row for %s should exist", id).isTrue();
                return new Object[] {resultSet.getBoolean("enabled"), resultSet.getString("severity")};
            }
        }
    }

    private void insertRule(final String id, final boolean enabled, final String severity) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO validation_rule (id, enabled, severity) VALUES (?, ?, ?)")) {
            statement.setString(1, id);
            statement.setBoolean(2, enabled);
            statement.setString(3, severity);
            statement.executeUpdate();
        }
    }

    @Nested
    @DisplayName("fresh environment (no collision)")
    class FreshEnvironment {

        @Test
        void migrate_fresh_environment_should_rename_all_pairs_without_error() throws Exception {
            assertThatCode(() -> flywayTargeting("latest").migrate()).doesNotThrowAnyException();

            assertThat(ruleExists("DR-SENT-002")).isFalse();
            assertThat(ruleExists("DR-DISQ-001")).isFalse();
            assertThat(ruleExists("DR-CTL-001")).isFalse();
            assertThat(ruleExists("DR-YRO-001")).isFalse();

            assertThat(ruleRow("DR-SENT-001")).containsExactly(true, "ERROR");
            assertThat(ruleRow("DR-DISQ-002")).containsExactly(true, "WARNING");
            assertThat(ruleRow("DR-CTL-003")).containsExactly(true, "WARNING");
            assertThat(ruleRow("DR-YRO-004")).containsExactly(true, "ERROR");
        }
    }

    @Nested
    @DisplayName("new id already occupied (collision)")
    class CollisionAtNewId {

        @Test
        void migrate_should_drop_stale_old_id_row_and_keep_existing_new_id_row() throws Exception {
            // V1.001-V1.005 seed the pre-renumbering rows; V1.006 (the renumbering migration under
            // test) has not run yet.
            flywayTargeting("1.005").migrate();
            assertThat(ruleExists("DR-SENT-002")).isTrue();
            assertThat(ruleExists("DR-SENT-001")).isFalse();

            // Simulate an STE reseed (or a PATCH-created override row) landing on the new id ahead
            // of this migration -- now both the old and new ids exist when V1.006 runs.
            insertRule("DR-SENT-001", false, "WARNING");

            assertThatCode(() -> flywayTargeting("latest").migrate()).doesNotThrowAnyException();

            // The stale old-id row is dropped; the row already occupying the new id is left
            // untouched (not overwritten by the old row's values).
            assertThat(ruleExists("DR-SENT-002")).isFalse();
            assertThat(ruleRow("DR-SENT-001")).containsExactly(false, "WARNING");
        }
    }

}
