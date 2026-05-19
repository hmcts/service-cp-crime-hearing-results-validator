# Feature Specification: Community Order End Date Validation

**Feature Branch**: `DD-41653-co-end-date-validation`  
**Created**: 2026-05-12  
**Status**: Draft  
**Input**: User description: "Community Order End Date Validation — AC2 end date before requirement end dates, AC3 end date less than 12 months when UPWR included"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Community Order End Date Must Not Be Earlier Than Any Requirement End Date (Priority: P1)

A caseworker recording a community order with one or more requirements (Curfew — CUR, Curfew with electronic monitoring — CURE, Further curfew — CURA, or Alcohol Abstinence and Monitoring Requirement — AAR) sets an order end date that falls before the end date of at least one of those requirements. The system must prevent the result from being shared until the order end date is extended to match or exceed all requirement end dates.

**Why this priority**: A community order cannot legally expire before its constituent requirements. This constraint ensures legal coherence between the parent order and its child requirements. Without it, the court record would contain contradictory dates.

**Independent Test**: Can be tested by submitting a draft hearing result with a community order whose end date is earlier than the end date of a Curfew requirement, and verifying the validation response names the offending requirement(s) in the error message.

**Acceptance Scenarios**:

1. **Given** a draft hearing result includes a COEW result on an offence and a Curfew (CUR) child requirement with an "End date" later than the COEW end date, **When** validation runs, **Then** an error is returned: "The end date of the order must match or be longer than the end date of Curfew (community requirement) - CUR", together with the names of affected defendants.
2. **Given** a draft hearing result includes a COEW result and a Curfew with electronic monitoring (CURE) child requirement with an "End date of tag" later than the COEW end date, **When** validation runs, **Then** an error names "Curfew with electronic monitoring - CURE" and lists affected defendants.
3. **Given** a draft hearing result includes a COEW result and a Further curfew requirement (CURA) child requirement with an "End date" later than the COEW end date, **When** validation runs, **Then** an error names "Further curfew requirement made - CURA" and lists affected defendants.
4. **Given** a draft hearing result includes a COEW result and an Alcohol Abstinence and Monitoring Requirement (AAR) child requirement with an "Until" date later than the COEW end date, **When** validation runs, **Then** an error names "Alcohol abstinence and monitoring - AAR" and lists affected defendants.
5. **Given** multiple requirements each with end dates later than the COEW end date, **When** validation runs, **Then** all offending requirements are named in the error output.
6. **Given** all requirement end dates are on or before the community order end date, **When** validation runs, **Then** no order-shorter-than-requirement error is raised.

---

### User Story 2 — Community Order Including Unpaid Work Must Last at Least 12 Months (Priority: P2)

A caseworker recording a community order that includes an Unpaid Work requirement (UPWR — "Unpaid work. Requirement to be completed within 12 months") sets an order end date that is less than 12 months from the date of the hearing. The system must prevent the result from being shared until the end date is at least 12 months after the hearing date.

**Why this priority**: Unpaid work requirements have a statutory minimum completion window of 12 months. An order shorter than 12 months cannot legally accommodate the requirement. This rule protects against a specific category of legally defective sentencing record.

**Independent Test**: Can be tested by submitting a draft hearing result with a community order containing a UPWR requirement whose order end date is fewer than 12 months from the hearing date, and verifying the validation error is returned.

**Acceptance Scenarios**:

1. **Given** a draft hearing result includes a community order result (COEW, COS, or CONI) with a UPWR child requirement and the order end date is before the date 12 months from the hearing date, **When** validation runs, **Then** an error is returned: "The end date of the order must be at least 12 months as it includes an unpaid work requirement", together with the names of affected defendants.
2. **Given** the order end date is exactly 12 months from the hearing date, **When** validation runs, **Then** no UPWR minimum-duration error is raised.
3. **Given** the order end date is more than 12 months from the hearing date, **When** validation runs, **Then** no UPWR minimum-duration error is raised.
4. **Given** a community order has no UPWR child requirement, **When** validation runs, **Then** no UPWR minimum-duration error is raised regardless of the order duration.

---

### Edge Cases

- What happens when a defendant has multiple offences each with a community order, and only one of those orders has an invalid end date? The error must identify only the defendants and offences with the problem.
- What happens when a community order result line is present but no end date has been recorded? The validation rules for these three conditions apply only when an end date is provided; absence of an end date is handled by mandatory-field validation (out of scope for this feature).
- What happens when both AC2 and AC3 apply simultaneously to the same order (e.g., end date is in the past, the order has a UPWR, and a CUR end date exceeds the order end date)? All applicable errors must be returned together; they are independent conditions.
- What happens when COS or CONI is the parent order type rather than COEW? All three rules apply equally to COEW, COS, and CONI.
- What happens when the hearing date itself is not available in the request? The service requires the hearing date in the request; if absent, all date-relative rules are unevaluable and must be treated as unable to fire (not as an error in themselves).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST raise an error when a community order end date is earlier than the end date of any Curfew (CUR), Curfew with electronic monitoring (CURE), Further curfew requirement (CURA), or Alcohol Abstinence and Monitoring Requirement (AAR) child result attached to that order.
- **FR-002**: The error message for FR-001 MUST identify the specific offending requirement(s) by name and short code (e.g., "Curfew (community requirement) - CUR") and list the names of all affected defendants.
- **FR-003**: The system MUST raise an error when a community order that includes an Unpaid Work (UPWR) child requirement has an end date fewer than 12 months from the hearing date. "Fewer than 12 months" means strictly before the same calendar date 12 months later (i.e., an end date of exactly hearing-date + 12 months is acceptable).
- **FR-004**: The error message for FR-003 MUST read: "The end date of the order must be at least 12 months as it includes an unpaid work requirement" and list the names of all affected defendants.
- **FR-005**: Both error conditions (FR-001, FR-003) MUST be classified as blocking errors (not warnings), preventing the result from being shared until resolved.
- **FR-006**: Multiple errors from different conditions MAY be returned simultaneously if more than one condition is triggered on the same draft hearing result.
- **FR-007**: Each error MUST be associated with the specific defendant(s) and offence(s) to which it applies.
- **FR-008**: For the requirement-comparison rule (FR-001), the date fields compared are: "End date" for CUR and CURA, "End date of tag" for CURE, and "Until" for AAR.

### Key Entities

- **Community Order Result**: A result line recorded against one or more offences with a short code of COEW (Community Order with Electronically Monitored Requirement), COS (Community Order), or CONI (Community Order — No Requirements). Has an end date attribute.
- **Requirement (Child Result)**: A child result line subordinate to a parent community order result. Relevant requirement types: CUR (Curfew — community requirement), CURE (Curfew with electronic monitoring), CURA (Further curfew requirement made), AAR (Alcohol abstinence and monitoring requirement), UPWR (Unpaid work — requirement to be completed within 12 months).
- **Date of Hearing**: The date on which the hearing takes place, sourced from the hearing record in the draft validation request. Used as the reference point for AC2 (12-month minimum).
- **Validation Error**: A blocking outcome produced when a condition is triggered. Carries a message, the names of affected defendants, and the relevant offence linkage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of draft hearing results where a community order end date is earlier than any attached requirement end date (CUR, CURE, CURA, AAR) are blocked from sharing, with all offending requirements named in the error.
- **SC-002**: 100% of draft hearing results containing a UPWR requirement where the order end date is fewer than 12 months from the hearing date are blocked from sharing, with the correct error message and affected defendant names presented.
- **SC-003**: Valid results (all end-date constraints satisfied) pass validation without false positives — no spurious end-date errors are raised.
- **SC-004**: When multiple end-date conditions apply simultaneously, all corresponding errors are returned in a single validation response (no silent suppression of secondary errors).

## Assumptions

- The date of the hearing is available as a field in the `DraftValidationRequest`. The date-relative rule (AC2) uses this date as its reference point.
- End date values are provided in a structured date format (day, month, year) rather than as a free-text string. The service can compare them as calendar dates.
- A child result (requirement) is identifiable as subordinate to a specific parent community order result by the existing data structure in `DraftValidationRequest`; no schema change to the request DTO is required.
- "12 months from the hearing date" is interpreted as the same calendar date one year later (e.g., hearing on 2026-05-12 → 12-month threshold is 2027-05-12). An end date of exactly 2027-05-12 is acceptable; 2027-05-11 is not.
- These two validation rules are independent of one another and of any existing validation rules. They do not modify the behaviour of DR-SENT-002 or any other existing rule.
- The feature is entirely server-side validation (within this service). The UI display of error placement (below labels, above fields, top-of-page summary) is a front-end concern and is out of scope for the results-validator service.
- Rules apply equally to all three parent order types (COEW, COS, CONI); there is no parent-type-specific branching.
- The severity ceiling mechanism (runtime DB overrides) applies to these rules as it does to all others. The rules ship with severity ERROR; DB overrides may cap them to WARNING at runtime but cannot promote below ERROR.
