# custodialIndicator — Field Notes

## Two sources of the value

There are two places in the data model that carry a custodial indicator:

### 1. `custodial_indicator` column (DB column, plain text)

- Added by Liquibase migration `022-alter-table-referencedataoffence-add-column-custodial-indicator.xml` as a nullable `text` column.
- Read by `ResultSetToOffenceConverter.java`:
  ```java
  .withCustodialIndicator(resultSet.getString("custodial_indicator"))
  ```
- Surfaced in the API response as the top-level field `custodialIndicator` (camelCase).
- Populated on write via `OffenceDomainToEntityConverter.java:63` and `PSSOffenceDomainToEntityConverter.java:161` from the domain object's `getCustodialIndicator()`.

### 2. `details_json → document.libra.custodialindicator.code` (embedded in JSONB blob)

- Stored inside the `details_json` JSONB column as part of the full offence document.
- Shape (from `referencedataoffences-domain-common/src/json/example/details.json`):
  ```json
  {
    "document": {
      "libra": {
        "custodialindicator": {
          "code": "Y",
          "description": "YES"
        }
      }
    }
  }
  ```
- Present in the response inside the `details` JSON object (not extracted to a separate field).
- The formal `details.json` schema does **not** list `custodialindicator` under `libra` — it is an extra field present in real data but absent from the schema definition.

---

## Intended precedence rule

> The `custodial_indicator` column takes precedence.  
> `document.libra.custodialindicator.code` is used only where the column has no value.

Older records (pre-migration `022`) will have `null` in the column; for those the fallback to the JSON field is needed.

---

## Current code — precedence logic is NOT implemented

Both query-side converters do a plain pass-through of the column value with no fallback:

| File | Line |
|------|------|
| `referencedataoffences-query-api/…/converter/OffenceEntityToDomainConverter.java` | 72 |
| `referencedataoffences-query-api/…/converter/OffenceEntityToDomainSearchConverter.java` | 88 |

```java
// current — no fallback
.withCustodialIndicator(offenceEntity.getCustodialIndicator())
```

---

## What needs to change

Replace the single line in **both** converters with a helper that implements the fallback:

```java
.withCustodialIndicator(resolveCustodialIndicator(offenceEntity))
```

```java
private static String resolveCustodialIndicator(final ReferencedataOffence offenceEntity) {
    if (offenceEntity.getCustodialIndicator() != null) {
        return offenceEntity.getCustodialIndicator();
    }
    // fall back to details_json → document.libra.custodialindicator.code
    final String details = offenceEntity.getDetails();
    if (isNullOrEmpty(details)) {
        return null;
    }
    try {
        return JsonPath.read(details, "$.document.libra.custodialindicator.code");
    } catch (PathNotFoundException e) {
        return null;
    }
}
```

The same JsonPath style is already used in
`referencedataoffences-domain-common/…/domain/common/ReferencedataOffence.java` (lines ~270–330)
for `miscode`, `libracategory`, `maxfinetypemagct`, etc.

---

## details_json full schema

Formal schema: `referencedataoffences-domain-common/src/json/details.json`

```
document
├── english
│   ├── title                       String
│   ├── standardoffencewording      String
│   └── legislation                 String
├── welsh
│   ├── welshoffencetitle           String
│   ├── welshstandardoffencewording String
│   └── welshlegislation            String
├── codes
│   ├── cjsoffencecode              String
│   └── dvlacode
│       ├── code                    String
│       └── description             String
├── libra
│   └── maxfinetypemagct
│       ├── code                    String
│       └── description             String
├── other
│   └── timelimitforprosecutions    String
└── ancillary
    ├── offencestartdate            String
    └── offenceenddate              String
```

**Extra fields present in real data but not in the schema** (accessed via JsonPath in `ReferencedataOffence.java`):

| JsonPath | Used for |
|----------|----------|
| `$.document.libra.miscode.code` | `getMisCode()` |
| `$.document.libra.miscode.description` | `getMisDescription()` |
| `$.document.libra.libracategory.code` | `getModeOfTrialDerived()` |
| `$.document.libra.custodialindicator.code` | fallback custodial indicator (not yet wired up) |
| `$.document.codes.mojstatscode` | `getMojStatsCode()` |
