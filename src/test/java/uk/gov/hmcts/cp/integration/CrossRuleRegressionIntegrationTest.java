package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-rule regression coverage. Proves that DR-SENT-002 and DR-DISQ-001 evaluate
 * independently per Constitution Principle III and FR-011 — each produces its own issue on a
 * single hearing payload that triggers both rules, with no interference.
 *
 * <p>Implements the scenario from {@code research.md} R10. Guards against a future preprocessor
 * dispatch refactor accidentally short-circuiting one rule when the other matches, and against
 * a YAML/CEL change in either rule that silently affects the other.
 */
class CrossRuleRegressionIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DISQ_RULE_ID = "DR-DISQ-001";

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void enableDisqRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(DISQ_RULE_ID)
                .enabled(true)
                .severity("WARNING")
                .updatedAt(Instant.now())
                .updatedBy("test-setup")
                .build());
        evictOverrideCache(DISQ_RULE_ID);
    }

    @AfterEach
    void restoreDisqRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(DISQ_RULE_ID)
                .enabled(false)
                .severity("WARNING")
                .updatedAt(Instant.now())
                .updatedBy("test-teardown")
                .build());
        evictOverrideCache(DISQ_RULE_ID);
    }

    private void evictOverrideCache(final String ruleId) {
        Cache cache = cacheManager.getCache("ruleOverrides");
        if (cache != null) {
            cache.evict(ruleId);
        }
    }

    @Test
    void hearing_triggering_both_rules_should_emit_one_error_and_one_warning() throws Exception {
        // off1..off4 — IMP custodial sentences with off1 = primary (first noInfo), off2/off3 noInfo,
        //              off4 concurrent → triggers DR-SENT-002 AC2 ERROR.
        // off5 — RT88026 + COEW + no DDOTE → triggers DR-DISQ-001 AC1 WARNING.
        String request = """
                {
                  "hearingId": "h-cross",
                  "hearingDay": "2026-04-26",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off2"},
                    {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off3"},
                    {"resultLineId": "rl4", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off4", "isConcurrent": true},
                    {"resultLineId": "rl5", "shortCode": "COEW", "category": "F", "label": "Convicted",
                     "defendantId": "d1", "offenceId": "off5"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault",
                     "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary",
                     "orderIndex": 3},
                    {"offenceId": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery",
                     "orderIndex": 4},
                    {"offenceId": "off5", "offenceCode": "RT88026",
                     "offenceTitle": "Dangerous driving", "orderIndex": 5}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)))
                .andExpect(jsonPath("$.errors.validationIssues[*].ruleId", contains("DR-SENT-002")))
                .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                .andExpect(jsonPath("$.errors.errorMessages[0]", is(
                        "Some offences do not include details of whether they are concurrent or"
                                + " consecutive. There should be only one primary sentence for each"
                                + " defendant, therefore one result without concurrent or consecutive"
                                + " information. This affects Alex Driver.")))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[*].ruleId", contains("DR-DISQ-001")))
                .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off5")))
                .andExpect(jsonPath("$.rulesEvaluated",
                        containsInAnyOrder("DR-SENT-002", "DR-DISQ-001", "DR-YRO-001")));
    }

    /**
     * Scenario R11: AC3 fires for Def1 (concurrent/consecutive on off1) and DR-DISQ-001 fires for
     * Def2 (relevant RTA offence with no extended-test disqualification). Different rules, different
     * defendants — each produces one independent OFFENCE-level warning.
     */
    @Test
    void different_rules_firing_for_different_defendants_should_produce_independent_warnings()
            throws Exception {
        String request = """
                {
                  "hearingId": "h-cross-r11",
                  "hearingDay": "2026-04-26",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1",
                     "isConcurrent": true, "consecutiveToOffence": "off2"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off2"},
                    {"resultLineId": "rl3", "shortCode": "COEW", "category": "F", "label": "Convicted",
                     "defendantId": "d2", "offenceId": "off3"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Alice", "lastName": "Smith"},
                    {"defendantId": "d2", "firstName": "Bob", "lastName": "Jones"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "RT88026", "offenceTitle": "Dangerous driving", "orderIndex": 3}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(2)))
                // DR-SENT-002 has priority 1000 so fires first → warnings[0]
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")))
                // DR-DISQ-001 has priority 2000 so fires second → warnings[1]
                .andExpect(jsonPath("$.warnings[1].ruleId", is("DR-DISQ-001")))
                .andExpect(jsonPath("$.warnings[1].validationLevel", is("OFFENCE")))
                .andExpect(jsonPath("$.warnings[1].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[1].affectedOffences[0].offenceId", is("off3")));
    }

    /**
     * Scenario R12: AC3 fires for both Def1 and Def2 (each has an offence marked both concurrent
     * and consecutive), and DR-DISQ-001 additionally fires for Def2 (relevant RTA offence with no
     * extended-test disqualification). This produces three independent OFFENCE-level warnings —
     * two AC3 (one per defendant) and one DR-DISQ-001 (Def2 only).
     */
    @Test
    void ac3_for_both_defendants_and_disq_for_def2_should_produce_three_warnings()
            throws Exception {
        String request = """
                {
                  "hearingId": "h-cross-r12",
                  "hearingDay": "2026-04-26",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1",
                     "isConcurrent": true, "consecutiveToOffence": "off2"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off2"},
                    {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d2", "offenceId": "off3",
                     "isConcurrent": true, "consecutiveToOffence": "off4"},
                    {"resultLineId": "rl4", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d2", "offenceId": "off4"},
                    {"resultLineId": "rl5", "shortCode": "COEW", "category": "F", "label": "Convicted",
                     "defendantId": "d2", "offenceId": "off5"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Alice", "lastName": "Smith"},
                    {"defendantId": "d2", "firstName": "Bob", "lastName": "Jones"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                    {"offenceId": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery", "orderIndex": 4},
                    {"offenceId": "off5", "offenceCode": "RT88026", "offenceTitle": "Dangerous driving", "orderIndex": 5}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(3)))
                .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-002')]", hasSize(2)))
                .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-DISQ-001')]", hasSize(1)))
                .andExpect(jsonPath("$.warnings[2].ruleId", is("DR-DISQ-001")))
                .andExpect(jsonPath("$.warnings[2].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[2].affectedOffences[0].offenceId", is("off5")));
    }
}
