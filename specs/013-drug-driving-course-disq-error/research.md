# Research: DR-DISQ-013 Drug Driving Course-Reduction Disqualification Error

## Decision 1: Preprocessor reuse vs new preprocessor

**Decision**: Create a new `DrugDrivingCourseReductionPreprocessor` with qualifier `drug-driving-course-reduction`.

**Rationale**: DR-DISQ-002 (`DisqualificationExtendedTestPreprocessor`) is absence-based — it fires when a qualifying offence has a non-excluded final result AND lacks an extended-test code. DR-DISQ-013 is presence-based — it fires when a drug driving offence has ANY result with DDRCO or DDRCOT present, regardless of result category. The CEL condition shape is the same (`qualifyingCount > 0`) but the Java logic differs enough that forcing DR-DISQ-002's preprocessor to handle both cases would require conditional branching that couples two unrelated rules. A new preprocessor keeps each rule's behaviour self-contained and is the correct application of Constitution Principle I and III.

**Alternatives considered**:
- Reuse `disqualification-extended-test` type with config flags — rejected because the category-F filter, excluded-codes gate, and inverted condition logic are hardwired in the existing preprocessor. Making them optional adds branching that merges two unrelated rule families.
- Repurpose `extendedTestShortCodes` in `PreprocessingDefinition` — rejected as misleading naming; a new field `courseReductionShortCodes` is clearer.

## Decision 2: Context record — new or reuse DisqualificationContext

**Decision**: Create a new `DrugDrivingContext` record.

**Rationale**: `DisqualificationContext` exposes `disqExtTestCount`, `finalCategoryCount`, and `excludedFinalCount` — none meaningful for DR-DISQ-013, where the only relevant signal is "does this offence have DDRCO or DDRCOT". A purpose-built `DrugDrivingContext` is simpler (4 fields vs 8), avoids confusion when reading test failures, and doesn't lock future drug-driving rules into the extended-test naming.

**Alternatives considered**:
- Reuse `DisqualificationContext` with the `disqExtTestCount` field repurposed as `courseReductionCount` — rejected because field names must communicate intent and `disqExtTestCount` named as such in an ERROR rule would confuse reviewers.

## Decision 3: PreprocessingDefinition extension

**Decision**: Add `List<String> courseReductionShortCodes` to `PreprocessingDefinition`.

**Rationale**: Keeps the YAML as the single source of truth for which codes trigger the rule (Constitution Principle I). Follows the established pattern (`extendedTestShortCodes`, `remandShortCodes`, etc.). The record already has Lombok `@Builder` so adding a field is backward-compatible for existing builders (new field defaults to null, existing rules don't use it).

**Alternatives considered**:
- Hardcode DDRCO/DDRCOT in the preprocessor — rejected because it moves policy out of YAML into Java, violating Constitution Principle I.

## Decision 4: CEL condition shape

**Decision**: Single condition `qualifyingCount > 0` → ERROR.

**Rationale**: The triggering combination is binary — either the offence is a drug driving offence with DDRCO/DDRCOT or it is not. No partial/graduated severity needed. Matches the simplest possible CEL expression pattern used across existing rules.

## Decision 5: Result category filter

**Decision**: Check ALL result lines (any category) for DDRCO/DDRCOT, not just category-F.

**Rationale**: A court user adding DDRCO to a drug driving offence produces an unlawful result regardless of the result line's category. Filtering by category would miss DDRCO/DDRCOT on non-final lines and fail to raise the error, allowing unlawful results through. The existing DR-DISQ-002 uses a category-F filter because its trigger condition requires a "final" result to be present — DR-DISQ-013's trigger is the mere presence of the unlawful code.

## Existing YAML structure confirmed (DR-DISQ-002)

The `DR-DISQ-013.yaml` will follow the DR-DISQ-002 YAML structure with:
- `preprocessing.type: "drug-driving-course-reduction"`
- `relevantOffenceCodes`: 5 drug driving codes
- `courseReductionShortCodes: [DDRCO, DDRCOT]`
- Single condition at `validationLevel: OFFENCE`
- `affectedOffenceSet: "qualifyingOffenceIds"`

## Existing integration test pattern confirmed

`DisqualificationExtendedTestRuleIntegrationTest` extends `IntegrationTestBase`, posts JSON to `/api/validation/validate`, pins `$.errors.validationIssues`, `$.warnings`, and filters by `ruleId`. The new `DrugDrivingCourseReductionRuleIT` follows the same pattern, filtering on `$.errors.validationIssues[?(@.ruleId=='DR-DISQ-013')]`.

## Liquibase migration

`V1.014__insert_dr_disq_013.sql` — inserts `('DR-DISQ-013', true, 'ERROR', now(), 'system')`.
Previous migration is V1.010; migrations V1.011–V1.013 are reserved for in-flight rules (DR-APP-009, DR-RESTRAO-010, and one other). V1.014 is confirmed by the user.
