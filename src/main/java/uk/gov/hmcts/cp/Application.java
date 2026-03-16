package uk.gov.hmcts.cp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

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

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}