# Tasks: CRA-256 – Custodial Sentence Recorded Against Non-Imprisonable Offence (Warning)

**Input**: Design documents from `specs/011-custodial-non-imprisonable-warning/`
**Branch**: `dev/CRA-256-custodial-sentence-recorded-against-non-imprisonable-offence`
**Rule ID**: `DR-SENT-011`
**Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)
**Generated**: 2026-10-06 (v3 — reference-data lookup approach)

**TDD Order (Constitution §VIII)**: Tests MUST be written before production code. Each test must run and FAIL at the assertion level — not a compilation error — before the production code that satisfies it is written.

**Already shipped** (no task needed): `DR-SENT-011.yaml`, `NonImprisonableOffenceContext.java`, `V1.012__insert_dr_sent_011.sql`, `IntegrationTestBase.REFERENCEDATA_OFFENCE_WIRE_MOCK`.

---

## Phase 1: Revert and Compile-safe Stub

**Purpose**: Revert the wrong-path DTO changes so the codebase compiles cleanly against `26.25`, and leave stub production code in place so failing tests can run (and fail at assertion level, not compilation level). No rule behaviour yet.

- [x] T001 Revert `api-hearing-results-validator = "26.26"` to `"26.25"` in `gradle/libs.versions.toml` (single-line change)
- [x] T002 Extend `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceResponse.java` — add `String custodialIndicator` and `OffenceDetails details` fields to the existing record; add four package-private nested records `OffenceDetails(OffenceDocument document)`, `OffenceDocument(LibraSection libra)`, `LibraSection(CustodialIndicatorCode custodialindicator)`, `CustodialIndicatorCode(String code)` — all annotated `@JsonIgnoreProperties(ignoreUnknown = true)`
- [x] T003 Declare stub `getCustodialIndicator(String offenceCode)` on `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` — body returns `Optional.empty()`; annotate with `@Cacheable(value = "referencedataOffences", key = "#offenceCode", unless = "#result == null")` — this lets tests compile and fail at assertion, not compilation
- [x] T004 Rewrite `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffencePreprocessor.java` as a compilable skeleton — add `private final ReferencedataOffenceClient referencedataOffenceClient` constructor field via `@RequiredArgsConstructor`; replace the removed `offence.getCustodialIndicator()` / `offence.getLibraCustodialIndicatorCode()` calls with a stub call to `referencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())`; `preprocess()` may still return `nonImprisonableCount = 0` for all offences — goal is compilation only
- [x] T005 Update `src/test/java/uk/gov/hmcts/cp/services/rules/ValidationRuleTestHelper.java` — remove the `offenceWithCustodialIndicator(String id, int countNumber, String title, String custodialIndicator, String libraCustodialIndicatorCode)` helper method and its `.custodialIndicator()` / `.libraCustodialIndicatorCode()` builder calls that do not compile against `OffenceDto` `26.25`; update callers in `NonImprisonableOffencePreprocessorTest.java` to use `offenceWithCode()` instead — confirm `gradle compileTestJava` passes

**Checkpoint**: `gradle compileTestJava` passes. All DR-SENT-011 tests compile and run; they fail at assertion level (no warnings produced by the stub implementation).

---

## Phase 2: Foundational — TDD for Client and Preprocessor

**Purpose**: Write all failing unit tests first (Red), then write the full implementations (Green). This delivers the core logic: `getCustodialIndicator()` with client-side column-over-JSON precedence, and the updated preprocessor that calls it.

**⚠️ Constitution §VIII**: Run each test task (`gradle test`) and confirm FAIL before the corresponding implementation task.

- [x] T006 Write failing `getCustodialIndicator` scenarios in `src/test/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClientTest.java` — add `@Nested @DisplayName("getCustodialIndicator")` block covering: column `"Y"` present → `Optional.of("Y")`; column `"N"` → `Optional.of("N")`; column `null` + JSON code `"N"` → `Optional.of("N")`; column `null` + JSON code `"Y"` → `Optional.of("Y")`; column `"Y"` + JSON code `"N"` → `Optional.of("Y")` (column wins); column `null` + no `custodialindicator` node → `Optional.empty()`; 404 response → `Optional.empty()`; timeout → `Optional.empty()`; empty `offences` array → `Optional.empty()`; use WireMock `jsonBody` stubs following the existing test pattern — run `gradle test --tests "uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClientTest"` and confirm new scenarios FAIL

- [x] T007 Rewrite `src/test/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffencePreprocessorTest.java` — mock `ReferencedataOffenceClient` via `@ExtendWith(MockitoExtension.class)`; construct preprocessor as `new NonImprisonableOffencePreprocessor(referencedataOffenceClient)`; stub `when(referencedataOffenceClient.getCustodialIndicator("RT88026")).thenReturn(Optional.of("N"))` etc.; assertions mirror the v2 test scenarios (column N → count 1, column Y → count 0, JSON fallback N → count 1, column Y + JSON N → count 0, missing → count 0, non-custodial short code → empty map); all 18 custodial short codes via `@ParameterizedTest`; run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonImprisonableOffencePreprocessorTest"` and confirm FAIL at assertion

- [x] T008 Implement `getCustodialIndicator()` fully in `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` — add `private Optional<String> fetchCustodialIndicator(String offenceCode)` (same HTTP call structure as `fetchMisCode`); add `private static Optional<String> resolveIndicator(ReferencedataOffenceResponse offence)` that applies column-wins-over-JSON precedence with full null-safety; replace the stub body with real logic; confirm `gradle test --tests "uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClientTest"` GREEN

- [x] T009 Implement full `preprocess()` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffencePreprocessor.java` — for each `offenceId` with a custodial result line: call `referencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())`; map `Optional.of("N")` → `nonImprisonableCount = 1L`, anything else → `0L`; produce `NonImprisonableOffenceContext` per qualifying offence; confirm `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonImprisonableOffencePreprocessorTest"` GREEN

**Checkpoint**: `gradle test --tests "...ReferencedataOffenceClientTest" --tests "...NonImprisonableOffencePreprocessorTest"` — all GREEN. Unit layer complete.

---

## Phase 3: US1 + US2 + US3 — Integration Tests (Priority: P1 + P2) 🎯 MVP

**Goal**: End-to-end verification that DR-SENT-011 fires correctly across all indicator scenarios (column N, JSON fallback, column precedence) via the full Spring context and `REFERENCEDATA_OFFENCE_WIRE_MOCK`.

**User Stories**: US1 (column N → warning), US2 (details_json fallback), US3 (column takes precedence)

**Independent Test**: `POST /api/validation/validate` with an `IMP` result and WireMock returning `custodialIndicator: "N"` produces exactly one DR-SENT-011 WARNING with `"A custodial sentence may not be available..."` and the correct `offenceId`.

- [x] T010 [US1] Rewrite `src/test/java/uk/gov/hmcts/cp/integration/NonImprisonableOffenceRuleIT.java` — replace existing test bodies to use `REFERENCEDATA_OFFENCE_WIRE_MOCK.stubFor(get(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH)).withQueryParam("cjsoffencecode", equalTo("RT88026")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", ...).withBody(...)))` pattern; add `@BeforeEach` to call `REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll()`; remove `"custodialIndicator"` and `"libraCustodialIndicatorCode"` from all request JSON — offences carry `"offenceCode": "RT88026"` only; AC1 stub body `{"offences":[{"offenceId":"abc","custodialIndicator":"Y"}]}`; AC2 stub body `{"offences":[{"offenceId":"abc","custodialIndicator":"N"}]}`; run `gradle test --tests "...NonImprisonableOffenceRuleIT.Ac1"` and `Ac2` and confirm GREEN

- [x] T011 [P] [US2] Add `Ac3ColumnBlankJsonN` and `Ac4ColumnBlankJsonY` nested classes to `NonImprisonableOffenceRuleIT.java` — AC3 stub body includes `"custodialIndicator": null, "details": {"document": {"libra": {"custodialindicator": {"code": "N", "description": "NO"}}}}` → asserts WARNING; AC4 same but `"code": "Y"` → asserts NO warning; confirm GREEN

- [x] T012 [P] [US3] Add `Ac5ColumnTakesPrecedence` nested class to `NonImprisonableOffenceRuleIT.java` — stub body `"custodialIndicator": "Y"` with `details.document.libra.custodialindicator.code = "N"` → asserts NO warning (column wins); add `Ac6NoIndicator` — stub body with no `custodialIndicator` and no `custodialindicator` node → asserts NO warning; confirm GREEN

- [x] T013 [US1] Add `BreadthCoverage` parameterised nested class to `NonImprisonableOffenceRuleIT.java` — `@ParameterizedTest @ValueSource(strings = {"IMP","YOI","DTO","EXTDVS","EXTDVSU","EXTIVS","STSDY","SPECC","SPECCC","SPECCD","SUSPS","SUSPSS","SUSPSNI","SUSPSNR","SUSPSD","SUSPSDS","SUSPSDNI","SUSPSDNR"})` — each iteration stubs `custodialIndicator: "N"` for the offence code and posts the given short code → asserts one DR-SENT-011 warning; run `gradle test --tests "...NonImprisonableOffenceRuleIT"` and confirm all GREEN

**Checkpoint**: `gradle test --tests "...NonImprisonableOffenceRuleIT"` — AC1–AC6 and all 18 short codes GREEN.

---

## Phase 4: US4 + US5 — Non-Custodial and Coexistence (Priority: P3)

**Goal**: No false positives for non-custodial results; DR-SENT-011 warning coexists with defendant-level and other offence-level warnings without suppression.

**User Stories**: US4 (non-custodial results → no warning), US5 (coexistence with other warnings)

**Independent Test**: AC7 — post an `COEW` result for an offence whose reference-data stub returns `custodialIndicator: "N"` and confirm zero DR-SENT-011 warnings AND zero WireMock calls.

- [x] T014 [US4] Add `Ac7NonCustodialResult` nested class to `NonImprisonableOffenceRuleIT.java` — request uses `shortCode: "COEW"`; register a stub but also verify `REFERENCEDATA_OFFENCE_WIRE_MOCK.verify(0, getRequestedFor(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH)))` (no lookup made); asserts no DR-SENT-011 warning; confirm GREEN

- [x] T015 [P] [US5] Add `OffenceLevelCoexistence` nested class to `NonImprisonableOffenceRuleIT.java` — hearing has two offences: one with `IMP` result (stub `custodialIndicator: "N"` → DR-SENT-011 fires) and one with `RESTRAO` result with two protected persons (→ DR-RESTRAO-010 fires); asserts DR-SENT-011 warning present, DR-RESTRAO-010 absent until CRA-260 merges (TODO marker in test)

- [x] T016 [P] [US5] Add `DefendantLevelCoexistence` nested class to `NonImprisonableOffenceRuleIT.java` — hearing has three IMP offences all concurrent/consecutive (no primary → DR-SENT-001 fires at DEFENDANT level), one of them has `custodialIndicator: "N"` stub (→ DR-SENT-011 fires at OFFENCE level); asserts both warnings present, `isValid: true`

**Checkpoint**: `gradle test --tests "...NonImprisonableOffenceRuleIT"` — all scenarios including US4 and US5 GREEN.

---

## Phase 5: Polish and Build Loop

**Purpose**: Live API stubs, static analysis clean, full build green.

- [x] T017 Update `wiremock/mappings/referencedataoffences-stub.json` — add stub entries for live API test offence codes (e.g. `live-nonimprisonable-n`, `live-nonimprisonable-y`) following the existing pattern in that file; these are picked up by docker-compose WireMock automatically
- [x] T018 Run `gradle checkstyleMain pmdMain` and fix any violations in changed files (`ReferencedataOffenceClient.java`, `ReferencedataOffenceResponse.java`, `NonImprisonableOffencePreprocessor.java`) — `maxWarnings = 0`
- [x] T019 Run `gradle build` — full build (Checkstyle + PMD + all unit + integration tests); fix any remaining failures
- [x] T020 [P] Verify `src/test/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfigurationTest.java` still passes — confirms DR-SENT-011 rule is loaded and `NonImprisonableOffencePreprocessor` registers under the `"non-imprisonable-offence"` qualifier

**Checkpoint**: `gradle build` GREEN — zero Checkstyle warnings, zero PMD violations, all tests pass.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: No dependencies — start immediately
- **Phase 2**: Depends on Phase 1 complete (codebase compiles) — BLOCKS Phase 3+
- **Phase 3**: Depends on Phase 2 complete
- **Phase 4**: Depends on Phase 3 (adds to the same IT class)
- **Phase 5**: Depends on Phases 3+4 complete

### Within Phase 2 (strict TDD sequence)

```
T006 (write failing client tests)   →  T008 (implement client method) → GREEN
T007 (write failing preprocessor tests, after T006 and T005) →  T009 (implement preprocessor) → GREEN
```

T006 and T007 can be written in parallel [P] (different files).
T009 should follow T008 (preprocessor calls the now-complete client method).

### Within Phase 3

```
T010 (base IT rewrite, AC1+AC2) → T011 [P] (AC3+AC4) + T012 [P] (AC5+AC6) + T013 (short codes)
```

T011, T012, T013 can be added in parallel — they are independent `@Nested` classes.

### Parallel Opportunities

```
T002, T003, T004 can proceed in parallel after T001 (different files)
T006 and T007 can be written in parallel after T005
T011, T012, T013 can be added in parallel after T010
T015 and T016 can be added in parallel (different @Nested classes)
T018 and T020 can run in parallel
```

---

## Implementation Strategy

### MVP (US1 only — column = N → warning)

1. Complete Phase 1 (Revert + Stub)
2. T006 → T008 (client), T007 → T009 (preprocessor)
3. T010 + T013 (AC1, AC2, all 18 short codes in IT)
4. **STOP and VALIDATE**: `gradle test --tests "...NonImprisonableOffenceRuleIT.Ac2"` GREEN

### Full delivery

Continue through Phase 3 (T011, T012), Phase 4, Phase 5.

---

## Notes

- [P] tasks = different files, no incomplete dependencies
- `DR-SENT-011.yaml`, `NonImprisonableOffenceContext.java`, `V1.012__insert_dr_sent_011.sql` are already shipped — no tasks for these
- `REFERENCEDATA_OFFENCE_WIRE_MOCK` is already in `IntegrationTestBase` — no infrastructure task needed
- The `referencedataOffences` Caffeine cache is shared between `lookupMisCode` (key = `offenceCode`) and `getCustodialIndicator` (key = `'ci:' + offenceCode`) — disjoint key spaces prevent cross-contamination; no config changes needed
- Do NOT add per-rule override / severity-ceiling ITs to `NonImprisonableOffenceRuleIT` — that coverage lives once in `ValidationRuleOverrideIntegrationTest`
- Commit after each phase checkpoint using Conventional Commits
