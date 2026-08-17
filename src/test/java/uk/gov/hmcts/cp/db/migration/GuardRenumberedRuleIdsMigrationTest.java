package uk.gov.hmcts.cp.db.migration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies V1.009's collision guard for the V1.006 rule-id renumbering.
 *
 * <p>Runs Flyway directly (via the Java API) against a throwaway PostgreSQL container so that
 * each scenario can control exactly which migrations have applied before a stale row is injected
 * -- this cannot be exercised through {@code IntegrationTestBase}, whose shared container always
 * migrates the full, collision-free chain once at startup.
 */
@Testcontainers
@DisplayName("V1.009 guard_renumbered_rule_ids migration")
class GuardRenumberedRuleIdsMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.3")
                    .withDatabaseName("guard_migration_test")
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
                assertThat(resultSet.next()).as("row for %s should exist", id).isTrue();
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

    private void deleteRule(final String id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM validation_rule WHERE id = ?")) {
            statement.setString(1, id);
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
            flywayTargeting("1.006").migrate();

            // V1.006 already renamed DR-SENT-002 -> DR-SENT-001 with no collision at this point.
            assertThat(ruleExists("DR-SENT-002")).isFalse();
            assertThat(ruleRow("DR-SENT-001")).containsExactly(true, "ERROR");

            // Simulate a stale PATCH-created (or reseeded) row landing back on the pre-renumbering
            // id before this migration runs -- now both the old and new ids exist.
            insertRule("DR-SENT-002", false, "WARNING");
            assertThat(ruleExists("DR-SENT-002")).isTrue();

            assertThatCode(() -> flywayTargeting("latest").migrate()).doesNotThrowAnyException();

            // The stale old-id row is dropped; the pre-existing new-id row is left untouched
            // (not overwritten by the stale row's values).
            assertThat(ruleExists("DR-SENT-002")).isFalse();
            assertThat(ruleRow("DR-SENT-001")).containsExactly(true, "ERROR");
        }
    }

    @Nested
    @DisplayName("old id reappears with new id absent (plain rename)")
    class OldIdOnly {

        @Test
        void migrate_should_rename_old_id_when_new_id_absent() throws Exception {
            flywayTargeting("1.006").migrate();

            // Simulate the new-id row having been removed, then a stale row reappearing under the
            // old (pre-renumbering) id -- only the old id exists when V1.009 runs.
            deleteRule("DR-SENT-001");
            insertRule("DR-SENT-002", false, "WARNING");

            assertThatCode(() -> flywayTargeting("latest").migrate()).doesNotThrowAnyException();

            assertThat(ruleExists("DR-SENT-002")).isFalse();
            assertThat(ruleRow("DR-SENT-001")).containsExactly(false, "WARNING");
        }
    }
}
