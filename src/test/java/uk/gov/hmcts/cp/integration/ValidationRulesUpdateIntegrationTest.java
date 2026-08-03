package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the PATCH /api/validation/rules/{ruleId} endpoint.
 * Covers the write path end-to-end: HTTP request → service → DB → cache eviction → response.
 */
class ValidationRulesUpdateIntegrationTest extends IntegrationTestBase {

    private static final String BASE_URL = "/api/validation/rules/";
    private static final String RULE_ID = "DR-SENT-002";

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private RuleOverrideService ruleOverrideService;

    @AfterEach
    void resetRule() {
        resetRuleOverride(ruleOverrideService, RULE_ID);
    }

    @Test
    void patch_enabledFalse_should_return_200_and_persist() throws Exception {
        mockMvc.perform(patch(BASE_URL + RULE_ID)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId", is(RULE_ID)));

        Optional<ValidationRuleEntity> saved = repository.findById(RULE_ID);
        assertThat(saved).isPresent();
        assertThat(saved.get().isEnabled()).isFalse();
        assertThat(saved.get().getUpdatedBy()).isEqualTo("test-user");
    }

    @Test
    void patch_severityWarning_should_return_200_and_persist() throws Exception {
        mockMvc.perform(patch(BASE_URL + RULE_ID)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\": \"WARNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId", is(RULE_ID)));

        Optional<ValidationRuleEntity> saved = repository.findById(RULE_ID);
        assertThat(saved).isPresent();
        assertThat(saved.get().getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void patch_unknownRuleId_should_return_404() throws Exception {
        mockMvc.perform(patch(BASE_URL + "DR-NONEXISTENT")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_noFields_should_return_400() throws Exception {
        mockMvc.perform(patch(BASE_URL + RULE_ID)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_invalidSeverityValue_should_return_400() throws Exception {
        mockMvc.perform(patch(BASE_URL + RULE_ID)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\": \"CRITICAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_unknownJsonField_should_return_400() throws Exception {
        mockMvc.perform(patch(BASE_URL + RULE_ID)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unknownField\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
