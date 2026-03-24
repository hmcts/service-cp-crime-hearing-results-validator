package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for actuator endpoints exposed by the application.
 */
class ActuatorIntegrationTest extends IntegrationTestBase {

    /**
     * Verifies the info endpoint exposes build metadata from the packaged artifact.
     */
    @Test
    void actuator_info_should_have_build_fields() throws Exception {
        String name = "service-cp-crime-hearing-results-validator";
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value(name))
                .andExpect(jsonPath("$.build.name").value(name))
                .andExpect(jsonPath("$.build.time").exists())
                .andExpect(jsonPath("$.build.version").exists());
    }

    /**
     * Verifies the info endpoint exposes git metadata populated by the build plugin.
     */
    @Test
    void actuator_info_should_have_gorylenko_git_fields() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.git.branch").exists())
                .andExpect(jsonPath("$.git.commit.id").exists())
                .andExpect(jsonPath("$.git.commit.time").exists());
    }

    /**
     * Verifies the health endpoint returns the expected status and health group information.
     */
    @Test
    void actuator_health_should_have_correct_fields() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.groups[0]").value("liveness"))
                .andExpect(jsonPath("$.groups[1]").value("readiness"));
    }
}
