package uk.gov.hmcts.cp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for V1.009__guard_renumbered_rule_ids.sql, the collision-safe follow-up to
 * V1.006's rule-id renumbering (PR #154 review, DD-43134).
 *
 * <p>Runs its own isolated Flyway migration chain against a dedicated schema on the shared
 * TestContainers Postgres instance, so it can reproduce the reported trigger -- a row already
 * sitting at a renumbered rule's old id (e.g. a stale "DR-DISQ-001" reintroduced by an STE-rebuild
 * reseed script or a PATCH-created override row, run against an environment that already applied
 * V1.006 and therefore already has the row at the new id, "DR-DISQ-002") -- without touching the
 * application's own already-migrated schema. Table references are schema-qualified rather than
 * relying on {@code SET search_path}, which would leak across pooled connections and corrupt
 * other tests sharing the same {@link DataSource}.
 */
class RuleIdRenumberGuardMigrationIntegrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "guard_migration_test";
    private static final String VALIDATION_RULE_TABLE = SCHEMA + ".validation_rule";

    @Resource
    private DataSource dataSource;

    @AfterEach
    void dropSchema() throws SQLException {
        dropSchemaIfExists();
    }

    /**
     * Without the V1.009 guard, re-seeding a rule's old id onto an already-renumbered environment
     * leaves both the old and new id rows present -- a latent primary-key collision waiting for
     * anything that repeats V1.006's rename. The guard migration must instead resolve the
     * collision itself: keep the row already at the new id and drop the stale old-id row.
     */
    @Test
    void migrate_should_drop_stale_old_id_row_when_new_id_row_already_exists() throws SQLException {
        dropSchemaIfExists();

        // migrate up to (and including) V1.006's rename, but stop short of the V1.009 guard
        final Flyway upToRenumber = Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target("1.008")
                .load();
        upToRenumber.migrate();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // simulate an STE-rebuild reseed / PATCH-created row reintroducing the pre-renumber id
            // onto an environment that already renamed DR-DISQ-001 to DR-DISQ-002 via V1.006
            statement.execute(
                    "INSERT INTO " + VALIDATION_RULE_TABLE + " (id, enabled, severity) "
                            + "VALUES ('DR-DISQ-001', false, 'WARNING')");
        }

        // run only the V1.009 guard against the reintroduced id -- pinned so a future V1.010+
        // added to the classpath doesn't silently start executing here too
        final Flyway ontoGuard = Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target("1.009")
                .load();
        ontoGuard.migrate();

        assertThat(remainingIdsAfterGuard()).containsExactly("DR-DISQ-002");
    }

    private Set<String> remainingIdsAfterGuard() throws SQLException {
        final Set<String> ids = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id FROM " + VALIDATION_RULE_TABLE
                             + " WHERE id IN ('DR-DISQ-001', 'DR-DISQ-002')")) {
            while (resultSet.next()) {
                ids.add(resultSet.getString("id"));
            }
        }
        return ids;
    }

    private void dropSchemaIfExists() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
