# Quickstart: DR-URG-008 Implementation (CRA-22)

**Branch**: `CRA-22-urgent-missing-result`
**Date**: 2026-09-09 (updated — CHD-2485 resolved)

---

## Prerequisites

1. Branch is already created: `CRA-22-urgent-missing-result`
2. `build.gradle` references `api-cp-crime-hearing-results-validator:0.2.8-cra-22` (CHD-2485 delivered — `BailStatusEnum bailStatus` is available on `OffenceDto`)
3. `gradle test` passes on `main` before starting

---

## Implementation order (TDD — tests first)

### Step 1: Update `PreprocessingDefinition`

Add one field to the `@Builder` record in
`src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`:

```java
List<String> bailEndingShortCodes
```

No existing tests break — both fields default to `null` via `@Builder`.

### Step 2: Write failing `ConditionalBailContextTest` (unit)

File: `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContextTest.java`

Cover:
- `toCelContext()` returns correct `conditionalBailOffenceCount`, `bailEndedCount`, `hasUrgentCount`
- `getDefendantIdSet("defendantId")` returns the single defendantId
- `getDefendantIdSet("unknown")` throws `IllegalArgumentException`
- `allOffenceIds()` returns the configured list
- `defendantName()` returns the name

Run: `gradle test --tests "*ConditionalBailContextTest"` — must fail (class not yet created).

### Step 3: Create `ConditionalBailContext`

File: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContext.java`

```java
public record ConditionalBailContext(
    String defendantId,
    String defendantName,
    long conditionalBailOffenceCount,
    long bailEndedCount,
    long hasUrgentCount,
    List<String> allOffenceIds
) implements RuleEvaluationContext {

    private static final String DEFENDANT_ID_SET = "defendantId";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
            "conditionalBailOffenceCount", conditionalBailOffenceCount,
            "bailEndedCount", bailEndedCount,
            "hasUrgentCount", hasUrgentCount
        );
    }

    @Override
    public List<String> getDefendantIdSet(String setName) {
        if (DEFENDANT_ID_SET.equals(setName)) {
            return List.of(defendantId);
        }
        throw new IllegalArgumentException("Unknown defendant set: " + setName);
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        if ("allOffenceIds".equals(setName)) {
            return allOffenceIds;
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }
}
```

Run: `gradle test --tests "*ConditionalBailContextTest"` — must go green.

### Step 4: Write failing `ConditionalBailPreprocessorTest` (unit)

File: `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessorTest.java`

Scenarios to cover (map to spec ACs):
- AC1: All conditional-bail offences → Category F result, no URGENT → fires
- AC2: All conditional-bail offences → DS result, no URGENT → fires
- AC3: All conditional-bail offences → RI result, no URGENT → fires
- AC4: All conditional-bail offences → WOFN result, no URGENT → fires
- AC5: Mixed bail-ending types (F + DS + RI), no URGENT → fires
- Suppression: URGENT present on one conditional-bail offence → does NOT fire
- Suppression: Not all conditional-bail offences have bail-ending results → does NOT fire
- Suppression: No conditional-bail offences (null or non-B bailStatus) → does NOT fire
- Multi-defendant: only qualifying defendant has warning; other defendant does not
- Null safety: null offences, null resultLines, null bailStatus

Run: `gradle test --tests "*ConditionalBailPreprocessorTest"` — must fail.

### Step 5: Create `ConditionalBailPreprocessor`

File: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessor.java`

```java
@Component
public class ConditionalBailPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "conditional-bail-urgent-check";
    private static final String URGENT_CODE = "URGENT";

    @Override
    public String type() { return QUALIFIER; }

    @Override
    public Map<String, ConditionalBailContext> preprocess(
            DraftValidationRequest request, PreprocessingDefinition config) {
        // See data-model.md for the full algorithm
        ...
    }
}
```

Run: `gradle test --tests "*ConditionalBailPreprocessorTest"` — must go green.
Run: `gradle checkstyleMain pmdMain` — must pass.

### Step 6: Create `DR-URG-008.yaml`

File: `src/main/resources/rules/DR-URG-008.yaml`

Use the schema from `data-model.md`. The preprocessing block contains only `bailEndingShortCodes` — conditional bail detection uses `BailStatusEnum.B` hardcoded in the preprocessor, not a YAML-configurable string.

### Step 7: Create Flyway migration

File: `src/main/resources/db/migration/V1.009__insert_dr_urg_008.sql`

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-URG-008', true, 'WARNING', now(), 'system');
```

### Step 8: Write failing integration test

File: `src/test/java/.../integration/UrgentMissingWarningIntegrationTest.java`

Extends `IntegrationTestBase`. Covers:
- POST to `/validate` with a request where conditional-bail offences have bail-ending results → response contains a `WARNING` at `DEFENDANT` level with the prescribed message text
- POST where URGENT is present → no warning
- POST where bail is not fully ended → no warning
- POST with two defendants, only one qualifying → one warning for qualifying defendant only

Run: `gradle test --tests "*UrgentMissingWarningIntegrationTest"` — must fail, then go green.
Run: `gradle test` — full suite must pass.

### Step 8b: Add AC5A scenario to `UrgentMissingWarningIntegrationTest` (FR-010)

**Scenario**: two CB offences — one with a bail-ending result (DS), one with no result lines at all.
Expected: no DR-URG-008 warning (unresulted CB offence prevents "all bail ended" condition).

```java
@Test
@DisplayName("AC5A — one CB offence bail-ended, one CB offence unresulted → no warning")
void one_cb_offence_bail_ended_one_cb_offence_unresulted_should_produce_no_warning()
        throws Exception {
    final String request = """
            {
              "hearingId": "h-ac5a",
              "hearingDay": "2026-05-06",
              "courtType": "CROWN",
              "resultLines": [
                {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                 "defendantId": "d1", "offenceId": "off-1"}
              ],
              "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
              "offences": [
                {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                 "orderIndex": 1, "bailStatus": "B"},
                {"offenceId": "off-2", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                 "orderIndex": 2, "bailStatus": "B"}
              ]
            }
            """;
    // off-2 is CB but has no result lines → unresulted CB offence → bailEndedCount (1) < conditionalBailOffenceCount (2) → no warning
}
```

Also add to `CRA-22.http` under the US3 section.

### Step 9: Run the full build loop

```bash
gradle build            # compile + checkstyle + PMD + all tests
```

Then run the agent loop (`code-reviewer` → `qa` → `spec-validator`) until all return PASS/COMPLIANT.

### Step 10: API live test

File: `src/apiTest/java/.../UrgentMissingWarningApiHttpLiveTest.java`

```bash
gradle api              # starts docker-compose, runs HTTP live tests
```

---

## Validation of the YAML rule

Confirm these CEL compile checks before committing:
```
conditionalBailOffenceCount > 0
  && bailEndedCount == conditionalBailOffenceCount
  && hasUrgentCount == 0
```

The `spec-validator` agent checks this at the end of the build loop.

---

## Test data builder hints

```java
// Offence with conditional bail
OffenceDto.builder()
    .offenceId("off-1")
    .bailStatus(OffenceDto.BailStatusEnum.B)
    .build();

// Bail-ending result line (Category F)
ResultLineDto.builder()
    .offenceId("off-1")
    .defendantId("def-1")
    .category(ResultLineDto.CategoryEnum.F)
    .shortCode("COEW")
    .build();

// Bail-ending result line (DS — sentence deferred, non-F)
ResultLineDto.builder()
    .offenceId("off-1")
    .defendantId("def-1")
    .shortCode("DS")
    .build();

// URGENT result line
ResultLineDto.builder()
    .offenceId("off-1")
    .defendantId("def-1")
    .shortCode("URGENT")
    .build();
```

---

## Regression guard

After the integration test passes, run:
```bash
gradle test --tests "*CrossRuleRegressionIntegrationTest"
```
This confirms DR-URG-008 does not interfere with other rules.
