package uk.gov.hmcts.cp.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

public class TestContainersInitialise
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:15.3")
                    .withDatabaseName("postgres")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withReuse(true);

    static {
        postgreSQLContainer.start(); // start once
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {

        TestPropertyValues.of(
                "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "spring.datasource.password=" + postgreSQLContainer.getPassword(),
                "spring.jms.listener.auto-startup=false",
                "management.health.jms.enabled=false",
                "spring.autoconfigure.exclude=uk.gov.hmcts.cp.filter.audit.config.ArtemisAuditAutoConfiguration"
        ).applyTo(context.getEnvironment());
    }
}
