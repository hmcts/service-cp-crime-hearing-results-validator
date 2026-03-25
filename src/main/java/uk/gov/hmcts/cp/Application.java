package uk.gov.hmcts.cp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Spring Boot entry point for the crime hearing results validator service.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "uk.gov.hmcts.cp",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uk\\.gov\\.hmcts\\.cp\\.filter\\.audit\\..*"
        )
)
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    /**
     * Boots the Spring application using the configured auto-configuration and component scan.
     *
     * @param args startup arguments passed to the JVM entry point
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
