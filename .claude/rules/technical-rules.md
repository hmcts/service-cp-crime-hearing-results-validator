# Coding Conventions — MOJ / CPP Standard

## Dependency Injection

- Constructor injection ONLY — NEVER use `@Autowired` on fields
- All injected fields MUST be `private final`
- Use Lombok `@RequiredArgsConstructor` OR explicit constructor

```java
// CORRECT
private final ValidationService validationService;

public ValidationController(ValidationService validationService) {
    this.validationService = validationService;
}

// WRONG — never do this
@Autowired
private ValidationService validationService;
```

## DTOs and Data Classes

- Java records for ALL DTOs and CEL context types — immutable by design
- Records for request bodies: `*Request` (e.g., `DraftValidationRequest`)
- Records for response bodies: `*Response` (e.g., `DraftValidationResponse`)
- Records for preprocessor outputs (e.g., `DefendantContext`)
- Use sealed interfaces for polymorphic types

## Error Handling

- Custom exceptions extending `RuntimeException`
- `@ControllerAdvice` for global exception handling
- Return `ProblemDetail` (RFC 9457) for all error responses
- NEVER swallow exceptions silently — always log or rethrow

## Logging

- SLF4J with Logback (via Spring Boot starter)
- Use Lombok `@Slf4j` or `private static final Logger log = LoggerFactory.getLogger(...)` — these are the only allowed forms
- MDC context: `correlationId` propagated by `TracingFilter` on every request
- `LogstashEncoder` emits structured JSON on the `json` Spring profile
- NEVER use `System.out.println`, `System.err.println`, or `Throwable#printStackTrace()` (Constitution Principle VII)
- NEVER log sensitive data (tokens, passwords, PII)

## Imports

- NEVER use wildcard imports (`import java.util.*`) — always use explicit imports for each class

## Enums and Routing

- Use Java enums for fixed value sets (statuses, types, severities)
- Switch expressions for routing — compiler enforces exhaustive coverage
- Include a `fromName(String)` or `fromValue(String)` factory method when parsing strings

## Naming Conventions

| Component       | Pattern                  | Example                              |
|-----------------|--------------------------|--------------------------------------|
| Service         | `*Service`               | `DefaultValidationService`           |
| Controller      | `*Controller`            | `ValidationController`               |
| Repository      | `*Repository`            | `ValidationRuleRepository`           |
| DTO (in)        | `*Request`               | `DraftValidationRequest`             |
| DTO (out)       | `*Response`              | `DraftValidationResponse`            |
| Rule            | `*Rule`                  | `CelValidationRule`                  |
| Preprocessor    | `*Preprocessor`          | `CustodialPreprocessor`              |
| Context record  | `*Context`               | `DefendantContext`                   |
| Exception       | `*Exception`             | `RuleEvaluationException`            |
| Config          | `*Configuration`         | `ValidationRuleAutoConfiguration`    |
| Test            | `*Test` / `*IT`          | `CelValidationRuleTest`              |

## Testing Conventions

- JUnit 5 + Mockito + AssertJ for unit tests
- `@ExtendWith(MockitoExtension.class)`
- `@Nested` classes with `@DisplayName` for grouped scenarios
- Method naming: `{action}_{scenario}_should_{expectation}`
- MockMvc for controller tests
- WireMock for external service stubs (use `dynamicPort()`)
- TestContainers for integration tests (suffix `*IT`); extend `IntegrationTestBase`
- Test commands:
  - `gradle test` — unit + integration
  - `gradle api` — live API tests (auto-manages docker-compose)
  - `gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation` — perf
- TDD: write the failing test first, see it fail for the right reason, then implement (Constitution Principle VIII)
- Logging in tests: SLF4J only (Constitution Principle VII)
