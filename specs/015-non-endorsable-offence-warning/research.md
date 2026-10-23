# Research: Non-Endorsable Offence Warning (CRA-337)

## Decision 1: Preprocessor strategy — one unified vs. two separate preprocessors

**Decision**: One unified preprocessor `NonEndorsableOffencePreprocessor` (qualifier: `"non-endorsable-offence"`) handling both the endorsement family and the disqualification family in a single pass per offence.

**Rationale**:
- Both families require an identical `getEndorsableFlag(offenceCode)` call per offence. A single preprocessor issues that call once per offence regardless of how many trigger families fire — the consolidated context record carries both `endorsementCount` and `disqualificationCount`.
- CRA-336 / DR-END-012 is already merged to main and production-tested. No existing preprocessor is touched.
- The context record shape is slightly wider than a single-family record but is still simple and directly readable — two long fields, two CEL variables.

**Alternatives considered**:
- *Two preprocessors (NonEndorsableEndorsementPreprocessor + NonEndorsableDisqualificationPreprocessor)*: requires two ref-data calls per offence when both families fire (even though Caffeine absorbs the second). Two separate preprocessors, two separate context records, two YAML files. Rejected in favour of the simpler single-pass approach.
- *Extend EndorsableOffencePreprocessor*: rejected — regression risk on DR-END-012; different check direction (DR-END-012 warns when endorsement is missing on an endorsable offence; this rule warns when endorsement/disqualification is present on a non-endorsable offence).

---

## Decision 2: YAML rule files — one file with two conditions vs. two files

**Decision**: One YAML file (`DR-END-014.yaml`) with two independent CEL conditions (AC1 endorsement, AC2 disqualification) backed by the single unified preprocessor.

**Rationale**:
- The unified preprocessor produces one `NonEndorsableOffenceContext` per offence, exposing both `endorsementCount` and `disqualificationCount` to CEL. Two conditions in one file evaluate against the same context — no second preprocessor pass needed.
- Independent enable/disable is still available at the condition level via the DB severity ceiling; if a specific condition must be silenced it can be managed via a DB override, keeping the simpler one-rule / one-preprocessor model.
- Each condition has its own `id` (AC1, AC2), `affectedOffenceSet`, and message template — they remain logically independent.

**Alternatives considered**:
- *Two YAML files (DR-END-014 + DR-END-015)*: independent DB enable/disable per file; simpler per-file reasoning. Rejected because it forces two preprocessor beans with duplicated ref-data logic.

---

## Decision 3: Fail-open vs. fail-closed on reference-data outage

**Decision**: Fail-open — treat an absent or unresolvable `endorsable_flag` as **non-endorsable** (flag = false), which means the warning **fires**.

**Rationale**:
- If we cannot confirm the offence is endorsable, the conservative default is to warn rather than suppress.
- This is the inverse of DR-END-012 (which warns when endorsement is missing). CRA-337's check fires when the offence is non-endorsable, so absent = warn.
- Consistent with the principle that a transient outage should not silently suppress a warning that might be legally relevant.

---

## Decision 4: Reference-data lookup — reuse vs. duplicate

**Decision**: Reuse `ReferencedataOffenceClient.getEndorsableFlag(offenceCode)` directly. No new client method needed.

**Rationale**: The method is already on main (merged as part of CRA-336). It is Caffeine-cached by offence code. The unified preprocessor calls it at most once per offence per request — the skip logic (no ref-data call when no trigger code is present) prevents any call for non-trigger offences.

---

## Decision 5: Non-endorsable disqualification codes — explicit exclusion vs. absence from trigger set

**Decision**: DDRN-family codes (DDRN, DDRNL, DDRA, DDRAL, DDRV, DDRVL) are simply not present in `disqualificationTriggerShortCodes`. No explicit exclusion list is needed.

**Rationale**: AC3 says "when a user records a DDRN-family result, then no warning is displayed." The simplest implementation: warn if any result line's shortCode is in the trigger set. Since DDRN etc. are not in the trigger set, they naturally produce no warning. No special-case logic needed in the preprocessor.

---

## Decision 6: SQL migration numbering

**Decision**: One new migration:
- `V1.015__insert_dr_end_014.sql` — inserts `DR-END-014` row

**Rationale**: V1.014 was the last migration (spec 013). One rule = one row = one migration. No V1.016 needed (DR-END-015 does not exist as a separate rule).

---

## Decision 7: Context record shape

**NonEndorsableOffenceContext**:
```java
public record NonEndorsableOffenceContext(
    String offenceId,
    long endorsementCount,       // 1 if offence is non-endorsable AND has an endorsement trigger; 0 otherwise
    long disqualificationCount   // 1 if offence is non-endorsable AND has a disqualification trigger; 0 otherwise
) implements RuleEvaluationContext
```
- `toCelContext()` → `Map.of("endorsementCount", endorsementCount, "disqualificationCount", disqualificationCount)`
- `getOffenceIdSet("nonEndorsableEndorsementOffenceIds")` → `List.of(offenceId)` if endorsementCount > 0
- `getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")` → `List.of(offenceId)` if disqualificationCount > 0

---

## Decision 8: Test isolation — cache pollution prevention

**Decision**: Use unique offence codes per test scenario (e.g. `"ne-end-lep"`, `"ne-disq-ddo"`) following the pattern established in `EndorsableOffenceRuleIT`.

**Rationale**: The Caffeine cache is shared across test methods within the same Spring context. Reusing the same offence code across tests risks a cache hit returning a stale response from an earlier test's WireMock stub.

---

## Decision 9: validationLevel field

**Decision**: Both conditions use `validationLevel: OFFENCE`.

**Rationale**: The spec explicitly states these are offence-level warnings, displayed below the relevant offence. This matches the pattern in DR-END-012 and DR-SEX-008.