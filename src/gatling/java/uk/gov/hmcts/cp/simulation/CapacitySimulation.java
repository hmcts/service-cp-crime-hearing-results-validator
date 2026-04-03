package uk.gov.hmcts.cp.simulation;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Production-capacity validation simulation (pipeline gate — assertions fail the build).
 *
 * Production peak load: ~0.31 req/sec (1,112 requests in the busiest hour).
 * We test at 10x peak (3.1 req/sec) to verify headroom.
 *
 * Two separate scenarios with independent injection profiles:
 *   - Warm-up: short ramp to prime JIT/class-loading, no assertions
 *   - Capacity: measured load with assertions
 *
 * Run locally:
 *   gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation
 *
 * Run against AKS:
 *   gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation \
 *     -Dgatling.baseUrl=http://service-route/results-validator
 */
public class CapacitySimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("gatling.baseUrl", "http://localhost:4550");

    private static final double PRODUCTION_PEAK_RPS = 0.31;
    private static final double HEADROOM_MULTIPLIER = 10.0;
    private static final double TARGET_RPS = PRODUCTION_PEAK_RPS * HEADROOM_MULTIPLIER;

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .header("Content-Type", "application/json")
        .header("CJSCPPUID", "nft-capacity-user");

    // Separate scenario for warm-up — runs first, completes before measured traffic starts
    private final ScenarioBuilder warmUpScenario = scenario("Warm-up")
        .exec(session -> session.set("payload", PayloadBuilder.randomWeighted()))
        .exec(
            http("Validate (warm-up)")
                .post("/api/validation/validate")
                .body(StringBody("#{payload}"))
                .check(status().is(200))
        );

    // Measured scenario — assertions apply to this
    private final ScenarioBuilder capacityScenario = scenario("Capacity")
        .exec(session -> session.set("payload", PayloadBuilder.randomWeighted()))
        .exec(
            http("Validate")
                .post("/api/validation/validate")
                .body(StringBody("#{payload}"))
                .check(status().is(200))
        );

    @Override
    public void before() {
        // Sanity check: send an AC2 payload and verify validation rules are active.
        // When disabled, the service returns mode="disabled" with no rules evaluated.
        // This would give a false-green NFT result with near-zero latency.
        System.out.println("Sanity check: verifying validation rules are enabled at " + BASE_URL);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/validation/validate"))
                .header("Content-Type", "application/json")
                .header("CJSCPPUID", "nft-sanity-check")
                .POST(HttpRequest.BodyPublishers.ofString(PayloadBuilder.ac2Error()))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body.contains("\"mode\":\"disabled\"")) {
                throw new RuntimeException(
                    "ABORTING: Validation rules are DISABLED (mode=disabled). "
                    + "NFT results would be meaningless. Check FEATURE_MANAGER_CONNECTION_STRING "
                    + "and feature toggle configuration.");
            }
            if (!body.contains("\"errors\":[{")) {
                throw new RuntimeException(
                    "ABORTING: AC2 payload did not produce errors. "
                    + "Rules may not be evaluating correctly. Response: " + body);
            }
            System.out.println("Sanity check passed: validation rules are active, AC2 produces errors.");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Sanity check failed: " + e.getMessage(), e);
        }
    }

    {
        setUp(
            // Phase 1: Warm-up — separate scenario, runs and completes first
            warmUpScenario.injectOpen(
                rampUsersPerSec(0).to(PRODUCTION_PEAK_RPS).during(Duration.ofMinutes(1))
            ),
            // Phase 2-4: Measured capacity — starts after warm-up completes
            capacityScenario.injectOpen(
                // Wait for warm-up to finish
                nothingFor(Duration.ofMinutes(1)),
                // Sustain production peak for 5 minutes
                constantUsersPerSec(PRODUCTION_PEAK_RPS).during(Duration.ofMinutes(5)),
                // Ramp to 10x peak over 2 minutes
                rampUsersPerSec(PRODUCTION_PEAK_RPS).to(TARGET_RPS).during(Duration.ofMinutes(2)),
                // Sustain 10x peak for 5 minutes
                constantUsersPerSec(TARGET_RPS).during(Duration.ofMinutes(5))
            )
        ).protocols(httpProtocol)
         .assertions(
             // Assertions scoped to "Capacity" scenario only — warm-up excluded
             details("Capacity / Validate").responseTime().percentile(95).lt(500),
             details("Capacity / Validate").responseTime().percentile(99).lt(1000),
             details("Capacity / Validate").successfulRequests().percent().gt(99.9)
         );
    }
}
