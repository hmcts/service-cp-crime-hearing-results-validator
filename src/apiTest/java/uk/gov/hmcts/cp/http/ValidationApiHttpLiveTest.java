package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live HTTP coverage for the draft validation endpoint against a running service instance.
 */
class ValidationApiHttpLiveTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers the empty-hearing scenario where no result lines exist and validation should return a
     * clean advisory response with no issues.
     */
    @Test
    void validate_empty_arrays_should_return_valid() throws Exception {
        String body = """
                {
                  "hearingId": "h1",
                  "caseId": "c1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [],
                  "defendants": [],
                  "offences": []
                }
                """;

        JsonNode json = postValidate(body);

        assertThat(json.get("isValid").asBoolean()).isTrue();
        assertThat(json.get("validationId").asText()).startsWith("val-");
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("mode").asText()).isEqualTo("advisory");
        assertThat(json.get("errors")).isEmpty();
        assertThat(json.get("warnings")).isEmpty();
        assertThat(json.get("rulesEvaluated").get(0).asText()).isEqualTo("DR-SENT-002");
    }

    /**
     * Covers AC1 for a single primary custodial offence plus a second offence marked concurrent, so
     * the request should remain valid with no warnings or errors.
     */
    @Test
    void ac1_single_offence_without_info_should_be_valid() throws Exception {
        String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true}
                  ],
                  "defendants": [{"id": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        JsonNode json = postValidate(body);

        assertThat(json.get("isValid").asBoolean()).isTrue();
        assertThat(json.get("errors")).isEmpty();
        assertThat(json.get("warnings")).isEmpty();
    }

    /**
     * Covers AC2 where more than one non-primary custodial offence omits concurrent or consecutive
     * information, producing a blocking error.
     */
    @Test
    void ac2_multiple_offences_missing_info_should_produce_error() throws Exception {
        String body = """
                {
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
                }
                """;

        JsonNode json = postValidate(body);

        assertThat(json.get("isValid").asBoolean()).isFalse();
        assertThat(json.get("errors")).hasSize(1);
        assertThat(json.get("errors").get(0).get("ruleId").asText()).isEqualTo("DR-SENT-002");
        assertThat(json.get("errors").get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(json.get("errors").get(0).get("message").asText()).startsWith("John Doe")
                .contains("Offence 2").contains("Offence 3").contains("do not include details");
    }

    /**
     * Covers AC3 where a custodial offence is marked both concurrent and consecutive, producing a
     * warning but not invalidating the payload.
     */
    @Test
    void ac3_offence_with_both_concurrent_and_consecutive_should_produce_warning() throws Exception {
        String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true, "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"id": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        JsonNode json = postValidate(body);

        assertThat(json.get("isValid").asBoolean()).isTrue();
        assertThat(json.get("errors")).isEmpty();
        assertThat(json.get("warnings")).hasSize(1);
        assertThat(json.get("warnings").get(0).get("ruleId").asText()).isEqualTo("DR-SENT-002");
        assertThat(json.get("warnings").get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(json.get("warnings").get(0).get("message").asText()).startsWith("John Doe")
                .contains("Offence 2").contains("concurrent").contains("consecutive");
    }

    /**
     * Covers AC4 where every custodial offence has relationship data and no primary sentence can be
     * identified, producing a warning only.
     */
    @Test
    void ac4_all_offences_have_info_no_primary_should_produce_warning() throws Exception {
        String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1", "isConcurrent": true},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"id": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        JsonNode json = postValidate(body);

        assertThat(json.get("isValid").asBoolean()).isTrue();
        assertThat(json.get("errors")).isEmpty();
        assertThat(json.get("warnings")).hasSize(1);
        assertThat(json.get("warnings").get(0).get("ruleId").asText()).isEqualTo("DR-SENT-002");
        assertThat(json.get("warnings").get(0).get("message").asText()).startsWith("John Doe")
                .contains("all offences include details").contains("no primary sentence");
    }

    private JsonNode postValidate(String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", "test-user");

        ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/validate",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }
}
