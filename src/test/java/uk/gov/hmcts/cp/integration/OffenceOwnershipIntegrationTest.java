package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Verifies required offence ownership at the HTTP boundary and across linked defendants. */
class OffenceOwnershipIntegrationTest extends IntegrationTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", "\"defendantId\": null,", "\"defendantId\": \"\",",
        "\"defendantId\": \"   \","})
    void validate_missing_null_or_blank_offence_owner_should_return_bad_request(String ownerField) throws Exception {
        // Ownership is mandatory even for a non-CB offence without result lines.
        String request = """
                {
                  "hearingId": "owner-required", "hearingDay": "2026-09-12", "courtType": "CROWN",
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Smith"}],
                  "resultLines": [],
                  "offences": [{"offenceId": "o1", %s "offenceCode": "TH68001", "offenceTitle": "Theft"}]
                }
                """.formatted(ownerField);
        mockMvc.perform(post("/api/validation/validate")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("offences[0].defendantId")));
    }

    @Test
    void validate_linked_unresulted_owner_should_suppress_only_that_person() throws Exception {
        String request = """
                {
                  "hearingId": "linked-owner", "hearingDay": "2026-09-12", "courtType": "CROWN",
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Smith"},
                    {"defendantId": "d2", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Smith"},
                    {"defendantId": "d3", "firstName": "Sam", "lastName": "Jones"}
                  ],
                  "resultLines": [
                    {"resultLineId": "r1", "defendantId": "d1", "offenceId": "o1", "shortCode": "DS", "label": "DS"},
                    {"resultLineId": "r3", "defendantId": "d3", "offenceId": "o3", "shortCode": "DS", "label": "DS"}
                  ],
                  "offences": [
                    {"offenceId": "o1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Theft", "bailStatus": "B"},
                    {"offenceId": "o2", "defendantId": "d2", "offenceCode": "TH68001", "offenceTitle": "Theft", "bailStatus": "B"},
                    {"offenceId": "o3", "defendantId": "d3", "offenceCode": "TH68001", "offenceTitle": "Theft", "bailStatus": "B"}
                  ]
                }
                """;
        mockMvc.perform(post("/api/validation/validate")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                .andExpect(jsonPath("$.warnings[0].affectedDefendants[*].defendantId", contains("d3")));
    }
}
