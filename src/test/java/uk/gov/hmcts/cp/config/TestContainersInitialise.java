package uk.gov.hmcts.cp.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots a reusable PostgreSQL test container and injects its connection details into Spring tests.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases") // an initializer, not a JUnit test class
public class TestContainersInitialise
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:15.3")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withReuse(true);

    static {
        POSTGRE_SQL_CONTAINER.start(); // start once
    }

    /**
     * Applies container-backed datasource properties to the integration-test application context.
     *
     * @param context Spring application context under initialization
     */
    @Override
    public void initialize(ConfigurableApplicationContext context) {

        TestPropertyValues.of(
                "spring.datasource.url=" + POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRE_SQL_CONTAINER.getUsername(),
                "spring.datasource.password=" + POSTGRE_SQL_CONTAINER.getPassword(),
                "spring.jms.listener.auto-startup=false",
                "management.health.jms.enabled=false",
                "spring.autoconfigure.exclude=uk.gov.hmcts.cp.filter.audit.config.ArtemisAuditAutoConfiguration"
        ).applyTo(context.getEnvironment());
    }
}
