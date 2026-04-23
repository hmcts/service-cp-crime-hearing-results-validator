package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test proving database overrides are applied during validation.
 */
class ValidationRuleOverrideIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String AC2_ERROR_REQUEST = """
            {
              "validationRequest": {
                "hearingId": "h1",
                "hearingDay": "2026-03-11",
                "courtType": "MAGISTRATES",
                "resultLines": [
                  {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                  {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2"},
                  {"id": "rl3", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off3"},
                  {"id": "rl4", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off4", "isConcurrent": true}
                ],
                "defendants": [{"id": "d1", "firstName": "John", "lastName": "Doe"}],
                "offences": [
                  {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                  {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                  {"id": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                  {"id": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery", "orderIndex": 4}
                ]
              },
              "offenceConvictions": []
            }
            """;

    @Resource
    private ValidationRuleRepository repository;

    /**
     * Verifies the seeded DR-SENT-002 override is present at startup and that the validation
     * response reflects its configured enabled/severity behaviour.
     */
    @Test
    void validate_should_use_seeded_override_from_startup() throws Exception {
        ValidationRuleEntity entity = repository.findById("DR-SENT-002").orElseThrow();
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getSeverity()).isEqualTo("ERROR");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AC2_ERROR_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }
}
