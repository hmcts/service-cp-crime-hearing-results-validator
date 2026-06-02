package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Framework-level integration tests for the runtime override mechanism: enable/disable, severity
 * ceiling capping, and severity-promotion-no-op. The mechanism is rule-agnostic and is proven
 * once here against the bundled DR-SENT-002 rule; new rules inherit this coverage and MUST NOT
 * duplicate per-rule override tests (see {@code .claude/rules/design_rules.md} §"Test the
 * framework once, not the rule again").
 */
class ValidationRuleOverrideIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULE_ID = "DR-SENT-002";

    private static final String AC2_ERROR_REQUEST = """
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

    private static final String AC3_WARNING_REQUEST = """
            {
              "hearingId": "h1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [
                {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2",
                 "isConcurrent": true, "consecutiveToOffence": "off1"}
              ],
              "defendants": [{"id": "d1", "firstName": "John", "lastName": "Doe"}],
              "offences": [
                {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
              ]
            }
            """;

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private CacheManager cacheManager;

    @AfterEach
    void resetSeededOverrideAndCache() {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(true)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-reset")
                .build());
        evictOverrideCache(RULE_ID);
    }

    /**
     * Verifies the seeded DR-SENT-002 override is present at startup and that the validation
     * response reflects its configured enabled/severity behaviour.
     */
    @Test
    void validate_should_use_seeded_override_from_startup() throws Exception {
        ValidationRuleEntity entity = repository.findById(RULE_ID).orElseThrow();
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

    /**
     * Verifies that setting a rule's {@code enabled=false} via the database row suppresses
     * issues from that rule at runtime, without service restart. Mechanism is proven once here
     * against DR-SENT-002; per-rule override IT is rejected by reviewers per
     * {@code .claude/rules/design_rules.md}.
     */
    @Test
    void validate_with_disabled_rule_should_emit_no_issues_for_that_rule() throws Exception {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(false)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-disabled")
                .build());
        evictOverrideCache(RULE_ID);

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AC2_ERROR_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    /**
     * Verifies that a database severity lower than the YAML severity caps the issue's emitted
     * severity downward. The DR-SENT-002 AC2 condition is YAML-{@code ERROR}; with the ceiling
     * set to {@code WARNING} the same payload still produces an issue, but the severity is
     * capped to {@code WARNING} (Constitution Principle VI — ceiling caps downward only).
     */
    @Test
    void validate_with_db_severity_lower_than_yaml_should_cap_downward() throws Exception {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(true)
                .severity("WARNING")
                .updatedAt(Instant.now())
                .updatedBy("test-capped")
                .build());
        evictOverrideCache(RULE_ID);

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AC2_ERROR_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is(RULE_ID)))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")));
    }

    /**
     * Verifies that a database severity higher than the YAML severity has no effect — the
     * ceiling cannot promote a YAML-WARNING condition to ERROR. DR-SENT-002 AC3 is
     * YAML-{@code WARNING}; with the ceiling set to {@code ERROR} the same AC3 payload should
     * still emit a {@code WARNING} (Constitution Principle VI — never promote).
     */
    @Test
    void validate_with_db_severity_higher_than_yaml_should_be_no_op() throws Exception {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(true)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-noop")
                .build());
        evictOverrideCache(RULE_ID);

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AC3_WARNING_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is(RULE_ID)))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")));
    }

    private void evictOverrideCache(final String ruleId) {
        Cache cache = cacheManager.getCache("ruleOverrides");
        if (cache != null) {
            cache.evict(ruleId);
        }
    }
}
