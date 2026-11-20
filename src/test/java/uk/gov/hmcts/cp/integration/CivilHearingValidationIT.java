package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.cp.services.feature.FeatureToggleConstants;
import uk.gov.hmcts.cp.services.feature.FeatureToggleService;

/**
 * CRA-391: hearings containing a civil offence ({@code "isCivil": true}) skip every rule unless the
 * {@code ValidateCivilHearings} flag is enabled. Uses the AC2 sentencing payload (which raises
 * DR-SENT-001) plus one civil offence, so the outcome shows whether rules ran. The flag-absent
 * default (skip) is covered by {@code ValidationControllerIntegrationTest}, which runs with no
 * feature label configured.
 */
@DisplayName("Civil hearings behind the ValidateCivilHearings flag")
class CivilHearingValidationIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String CIVIL_FLAG = FeatureToggleConstants.VALIDATE_CIVIL_HEARINGS.getFeatureName();

    private static final String CIVIL_HEARING_REQUEST = """
            {
              "hearingId": "h1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [
                {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2"},
                {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off3"},
                {"resultLineId": "rl4", "shortCode": "LAWD", "label": "Legal Aid Withdrawn", "defendantId": "d1", "offenceId": "off4"}
              ],
              "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
              "offences": [
                {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                {"offenceId": "off2", "defendantId": "d1", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                {"offenceId": "off3", "defendantId": "d1", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                {"offenceId": "off4", "defendantId": "d1", "offenceCode": "CV001", "offenceTitle": "Civil matter", "orderIndex": 4, "isCivil": true}
              ]
            }
            """;

    @MockitoBean
    private FeatureToggleService featureToggleService;

    @BeforeEach
    void enableAllFlagsByDefault() {
        when(featureToggleService.isFeatureEnabled(anyString(), anyBoolean())).thenReturn(true);
    }

    @Test
    void validate_civilHearing_and_civilFlagDisabled_should_skip_all_rules_and_return_valid() throws Exception {
        when(featureToggleService.isFeatureEnabled(eq(CIVIL_FLAG), anyBoolean())).thenReturn(false);

        performValidate()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.mode", is("advisory")))
                .andExpect(jsonPath("$.rulesEvaluated", empty()))
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    void validate_civilHearing_and_civilFlagEnabled_should_evaluate_all_rules() throws Exception {
        performValidate()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(false)))
                .andExpect(jsonPath("$.rulesEvaluated", hasItem("DR-SENT-001")))
                .andExpect(jsonPath("$.errors.validationIssues[*].ruleId", hasItem("DR-SENT-001")));
    }

    private ResultActions performValidate() throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIVIL_HEARING_REQUEST));
    }
}
