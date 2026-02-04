# Implementation Plan: Universal Secret Interface

**Branch**: `001-universal-secret-interface` | **Date**: 2026-01-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-universal-secret-interface/spec.md`

## Summary

Build a Spring Boot starter library that provides a universal interface for retrieving secrets from multiple cloud providers (AWS Secrets Manager, GCP Secret Manager) via annotation-based injection. The library abstracts provider-specific details behind a common interface, enabling developers to switch providers through configuration alone. Initial release focuses on read-only operations with support for caching, retry logic, and a local development provider.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Build Tool**: Maven
**Framework**: Spring Boot 3.x (Spring Framework 6.x)
**Primary Dependencies**:
- Spring Boot Starter (core auto-configuration)
- AWS SDK v2 for Java (Secrets Manager client)
- Google Cloud Secret Manager client library
- Project Lombok (boilerplate reduction)
- SLF4J (logging facade)
- Jackson (JSON parsing for structured secrets)
- Caffeine (in-memory caching)

**Storage**: N/A (secrets stored in external providers)
**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (for integration tests)
**Target Platform**: JVM 21+, any OS supporting Spring Boot
**Project Type**: Library (Spring Boot Starter)
**Performance Goals**: <500ms startup overhead for 10 secrets (per SC-003)
**Constraints**: Read-only operations; no secret creation/modification
**Scale/Scope**: Library consumed by enterprise Spring Boot applications

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence/Action |
|-----------|--------|-----------------|
| I. Security-First | ✅ PASS | Secrets never logged (FR-011); TLS enforced by cloud SDKs; audit logging required |
| II. Test-Driven Development | ✅ PASS | Unit tests (>80% coverage) + integration tests planned; TDD workflow will be followed |
| III. API-First Design | ✅ PASS | Annotation-based API contract defined; configuration properties documented |
| IV. Spring Best Practices | ✅ PASS | Constructor injection via Lombok; auto-configuration; profiles support |
| V. KISS Principle | ✅ PASS | Single interface `SecretProvider`; no unnecessary abstractions; simple cache with Caffeine |
| VI. Extensibility | ✅ PASS | Interface-based provider abstraction; new providers via implementing `SecretProvider` |

**Security Requirements Compliance**:
- ✅ Data Protection: Cloud SDKs use TLS; no local storage of secrets
- ✅ Audit Logging: All access events logged (without values)
- ✅ Input Validation: Secret key validation before provider calls
- ✅ Dependency Management: Maven dependency check plugin for vulnerability scanning

## Project Structure

### Documentation (this feature)

```text
specs/001-universal-secret-interface/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (configuration schema)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── io/github/timhwang777/unisecret/
│   │       ├── annotation/
│   │       │   └── SecretValue.java
│   │       ├── config/
│   │       │   ├── SecretManagerAutoConfiguration.java
│   │       │   └── SecretManagerProperties.java
│   │       ├── provider/
│   │       │   ├── SecretProvider.java
│   │       │   ├── AwsSecretProvider.java
│   │       │   ├── GcpSecretProvider.java
│   │       │   └── LocalSecretProvider.java
│   │       ├── cache/
│   │       │   └── SecretCache.java
│   │       ├── processor/
│   │       │   └── SecretValueBeanPostProcessor.java
│   │       ├── exception/
│   │       │   ├── SecretNotFoundException.java
│   │       │   ├── SecretProviderException.java
│   │       │   └── SecretConfigurationException.java
│   │       └── util/
│   │           └── RetryHelper.java
│   └── resources/
│       ├── META-INF/
│       │   └── spring/
│       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       └── application.yml (example configuration)
└── test/
    ├── java/
    │   └── io/github/timhwang777/unisecret/
    │       ├── unit/
    │       │   ├── SecretCacheTest.java
    │       │   ├── AwsSecretProviderTest.java
    │       │   ├── GcpSecretProviderTest.java
    │       │   ├── LocalSecretProviderTest.java
    │       │   └── SecretValueBeanPostProcessorTest.java
    │       └── integration/
    │           ├── AwsIntegrationTest.java
    │           ├── GcpIntegrationTest.java
    │           └── LocalProviderIntegrationTest.java
    └── resources/
        └── application-test.yml
```

**Structure Decision**: Single Maven module for the Spring Boot starter library. Standard `src/main/java` and `src/test/java` layout following Maven conventions. Package structure organized by concern (annotation, config, provider, cache, processor, exception, util).

## Complexity Tracking

No constitution violations requiring justification. Design follows KISS principle with minimal abstractions:
- Single `SecretProvider` interface (not a hierarchy)
- Simple in-memory cache using Caffeine (no distributed cache)
- BeanPostProcessor for annotation processing (standard Spring pattern)
