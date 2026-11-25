# Quickstart: Pre-Release Bail Condition Error (DR-BAIL-015 / CRA-363)

## What this feature does

Adds validation rule **DR-BAIL-015** (priority 15000, severity ERROR) that blocks sharing when a
conditional bail result (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC) is paired with
one or more pre-release bail condition results (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3,
PRBCSE1, PRBCSE2, PRBCT) as child results on the **same offence**.

Five error messages are produced depending on parent result type and defendant age:
- **Error A** — adult (≥18), remand on conditional bail parent
- **Error B** — youth (<18), remand on conditional bail parent
- **Error C** — combined/no-DOB, remand on conditional bail parent (summary) or mixed-age hearing
- **Error D** — CCIC parent (any age)
- **Error E** — CCSC parent (any age)

## Build and test commands

```bash
# Full build (Checkstyle + PMD + unit + integration)
./gradlew build

# Unit + integration tests only
./gradlew test

# Single test class
./gradlew test --tests "uk.gov.hmcts.cp.services.rules.cel.PreReleaseBailConditionPreprocessorTest"
./gradlew test --tests "uk.gov.hmcts.cp.integration.PreReleaseBailConditionRuleIT"

# API tests (requires docker-compose stack)
./gradlew api
```

## Files to create / modify

| Action | File |
|--------|------|
| NEW    | `src/main/resources/rules/DR-BAIL-015.yaml` |
| NEW    | `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailConditionPreprocessor.java` |
| NEW    | `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContext.java` |
| MODIFY | `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` (4 new fields) |
| NEW    | `src/main/resources/db/migration/V1.017__pre_release_bail_condition_error.sql` |
| NEW    | `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailConditionPreprocessorTest.java` |
| NEW    | `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContextTest.java` |
| NEW    | `src/test/java/uk/gov/hmcts/cp/integration/PreReleaseBailConditionRuleIT.java` |
| NEW    | `src/apiTest/java/uk/gov/hmcts/cp/http/PreReleaseBailConditionApiHttpLiveTest.java` |
| MODIFY | `README.md` (add DR-BAIL-015 row to validation rules table) |
| NEW    | `CRA-363.http` (local HTTP test file, all ACs) |

## TDD order (Constitution Principle VIII)

1. Write failing `PreReleaseBailContextTest` (compilation fails) → create `PreReleaseBailContext`
2. Write failing `PreReleaseBailConditionPreprocessorTest` → create `PreReleaseBailConditionPreprocessor` + modify `PreprocessingDefinition`
3. Write failing `PreReleaseBailConditionRuleIT` → create `DR-BAIL-015.yaml` + SQL migration
4. Run `./gradlew test` — all pass
5. Write `PreReleaseBailConditionApiHttpLiveTest`
6. Run `./gradlew build` (Checkstyle + PMD + tests)
7. Run spec-validator agent → COMPLIANT
8. Run code-reviewer agent → PASS
9. Run qa agent → PASS

## Key design decisions (see data-model.md for full detail)

- **One context per triggered offence** (not per defendant). This is per-offence validation.
- **Hearing-wide flags** (`hearingHasRemandAdult`, `hearingHasRemandYouth`) are pre-computed in a two-pass algorithm so CEL expressions can reference them.
- **Error C consolidation**: AC_MIXED_ADULT and AC_MIXED_YOUTH both set `errorMessageTemplate` to identical Error C text. `DefaultValidationService` groups page-level messages by `ruleId::errorMessage`, so they merge into one summary entry with all defendant names.
- **No new framework changes** required. The existing `consolidatePageLevelError` mechanism and defendant-name accumulation are sufficient.
