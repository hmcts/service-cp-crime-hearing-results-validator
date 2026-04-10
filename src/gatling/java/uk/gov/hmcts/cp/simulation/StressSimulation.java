package uk.gov.hmcts.cp.simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.stressPeakUsers;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static jodd.util.StringUtil.isNotEmpty;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * Exploratory stress/spike simulation — no assertions, report-only.
 *
 * <p>Use this to find the breaking point. Results are informational;
 * this simulation does NOT gate the pipeline.
 *
 * <p>Run locally:
 *   gradle gatlingRun-uk.gov.hmcts.cp.simulation.StressSimulation
 *
 * <p>Run against AKS:
 *   gradle gatlingRun-uk.gov.hmcts.cp.simulation.StressSimulation \
 *     -Dgatling.baseUrl=http://service-route/results-validator
 */
public class StressSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("gatling.baseUrl", "http://localhost:4550");
    private static final String HOST_HEADER = System.getProperty("gatling.hostHeader", "");

    private final HttpProtocolBuilder httpProtocol = buildHttpProtocol();

    private static HttpProtocolBuilder buildHttpProtocol() {
        HttpProtocolBuilder builder = http
                .baseUrl(BASE_URL)
                .header("Content-Type", "application/json")
                .header("CJSCPPUID", "nft-stress-user");
        if (HOST_HEADER != null && isNotEmpty(HOST_HEADER)) {
            builder = builder.header("Host", HOST_HEADER);
        }
        return builder;
    }

    private final ScenarioBuilder rampScenario = scenario("Stress Ramp")
            .exec(session -> session.set("payload", PayloadBuilder.randomWeighted()))
            .exec(
                    http("Validate")
                            .post("/api/validation/validate")
                            .body(StringBody("#{payload}"))
                            .check(status().saveAs("httpStatus"))
            );

    private final ScenarioBuilder spikeScenario = scenario("Spike Burst")
            .exec(session -> session.set("payload", PayloadBuilder.randomWeighted()))
            .exec(
                    http("Validate (spike)")
                            .post("/api/validation/validate")
                            .body(StringBody("#{payload}"))
                            .check(status().saveAs("httpStatus"))
            );

    {
        setUp(
            // Phase 1: Linear ramp from 0 to 50 req/sec over 10 minutes to find the ceiling
            rampScenario.injectOpen(
                rampUsersPerSec(0).to(50).during(Duration.ofMinutes(10))
            ),
            // Phase 2: After a 30-second pause, sudden spike of 200 users in 10 seconds
            spikeScenario.injectOpen(
                nothingFor(Duration.ofSeconds(30)),
                stressPeakUsers(200).during(Duration.ofSeconds(10)),
                // Hold at 20 req/sec for 2 minutes after spike
                constantUsersPerSec(20).during(Duration.ofMinutes(2))
            )
        ).protocols(httpProtocol);
        // No assertions — this is exploratory. Inspect the HTML report manually.
    }
}
