# Workflow: Mandatory Build Loop

Every non-trivial code change MUST follow this cycle:

```
Spec → Write → Code Review (agent) → QA (agent) → Spec Validate (agent) → Fix → Ship
```

- **Spec:** Define or update the YAML rule file (`src/main/resources/rules/DR-*.yaml`) BEFORE writing Java (Constitution Principle I — YAML/CEL Rule-First)
- **Spec Validate:** Run `spec-validator` agent to check YAML rules conform to `RuleDefinition`, that every `preprocessing.type` resolves to a registered preprocessor bean, and that every CEL expression compiles

Loop repeats until ALL agents return PASS / COMPLIANT.

## What Requires the Loop

| Must Go Through Loop                          | Exempt                         |
|-----------------------------------------------|--------------------------------|
| New / modified YAML rule (`DR-*.yaml`)        | Markdown / docs only           |
| New / modified Java class                     | Whitespace / import only       |
| New / modified test class                     | CLAUDE.md and rule updates     |
| Liquibase migrations                          | README changes                 |
| Dockerfile changes                            |                                |
| CI/CD pipeline config                         |                                |
| Helm chart or values                          |                                |

## TDD is Non-Negotiable (Constitution Principle VIII)

- Write the failing test first; confirm it fails for the *correct* reason (assertion failure, not a compilation error)
- Then write the minimum production code to make it pass
- Then refactor with the test still green
- Commit history MUST show the failing test was authored at or before the production code

## Agent Definitions

### code-reviewer (Read only)
- Spawned as sub-agent with Read-only tools
- Analyses code for: logic errors, null safety, layering violations, secrets, patterns, `System.out` usage, severity-ceiling promotion
- Returns: **PASS** or **NEEDS CHANGES** with severity-rated findings
- NEVER modifies code — reports only

### qa (Read, Write, Bash)
- Spawned as sub-agent
- Generates unit + integration tests (JUnit 5 + Mockito + AssertJ + MockMvc)
- Verifies TDD discipline (failing test authored before production code)
- Runs `gradle test`
- Returns: **PASS** or **FAIL** with test results
- NEVER fixes production code — only writes tests

### software-engineer (Full access)
- For full feature implementation tasks
- Follows all rules in `technical-rules.md` and the constitution
- Runs `gradle build` (and `gradle checkstyleMain pmdMain` for new Java) after changes

### spec-validator (Read only)
- Spawned as sub-agent with Read-only tools
- Reads YAML rule files in `src/main/resources/rules/` and compares against `RuleDefinition` schema, preprocessor registry, and CEL expression validity
- Checks: schema compliance, preprocessor dispatch wiring, CEL compile, message-template placeholders, severity-ceiling monotonicity
- Returns: **COMPLIANT** or **DRIFT DETECTED** with severity-rated findings
- NEVER modifies code — reports only

### research (Read, Glob, Grep, WebSearch)
- For deep codebase investigation
- Cross-references design documents
- Returns structured findings with citations

## Critical Principle

**Agents are reporters, not fixers.** The parent agent (or developer) reads agent reports and applies all fixes. This prevents conflicting changes and keeps the team in control.
