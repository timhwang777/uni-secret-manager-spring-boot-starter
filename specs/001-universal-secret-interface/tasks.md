# Tasks: Universal Secret Interface

**Input**: Design documents from `/specs/001-universal-secret-interface/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: Tests are included as this is a library with TDD requirement per the constitution (>80% coverage).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Package base**: `src/main/java/io/github/timhwang777/unisecret/`
- **Test base**: `src/test/java/io/github/timhwang777/unisecret/`
- **Resources**: `src/main/resources/`

---

## Phase 1: Setup (Project Infrastructure)

**Purpose**: Maven project initialization and basic structure

- [X] T001 Create Maven pom.xml with Spring Boot 3.x parent, Java 21, and dependencies (spring-boot-autoconfigure, aws-secretsmanager, google-cloud-secretmanager, caffeine, lombok, jackson, junit5, mockito, testcontainers)
- [X] T002 [P] Create base package structure: annotation/, config/, provider/, cache/, processor/, exception/, util/ in src/main/java/io/github/timhwang777/unisecret/
- [X] T003 [P] Create test package structure: unit/, integration/ in src/test/java/io/github/timhwang777/unisecret/
- [X] T004 [P] Create META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports in src/main/resources/
- [X] T005 [P] Create application-test.yml in src/test/resources/ with test configuration

---

## Phase 2: Foundational (Core Infrastructure)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

### Exception Hierarchy

- [X] T006 [P] Create SecretException base class in src/main/java/io/github/timhwang777/unisecret/exception/SecretException.java
- [X] T007 [P] Create SecretNotFoundException with ProviderAttempt record in src/main/java/io/github/timhwang777/unisecret/exception/SecretNotFoundException.java
- [X] T008 [P] Create SecretProviderException with retryable flag in src/main/java/io/github/timhwang777/unisecret/exception/SecretProviderException.java
- [X] T009 [P] Create SecretConfigurationException in src/main/java/io/github/timhwang777/unisecret/exception/SecretConfigurationException.java
- [X] T010 [P] Create SecretParsingException in src/main/java/io/github/timhwang777/unisecret/exception/SecretParsingException.java

### Core Types

- [X] T011 [P] Create ProviderType enum (AWS, GCP, LOCAL) in src/main/java/io/github/timhwang777/unisecret/provider/ProviderType.java
- [X] T012 [P] Create SecretReference value object in src/main/java/io/github/timhwang777/unisecret/provider/SecretReference.java
- [X] T013 Create SecretProvider interface with Optional<String> return type in src/main/java/io/github/timhwang777/unisecret/provider/SecretProvider.java (depends on T011)

### Configuration Properties

- [X] T014 Create SecretManagerProperties with nested AWS, GCP, Local, Cache, Retry configs in src/main/java/io/github/timhwang777/unisecret/config/SecretManagerProperties.java (depends on T011)

### Unit Tests for Foundational

- [X] T015 [P] Create SecretReferenceTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretReferenceTest.java
- [X] T016 [P] Create ProviderTypeTest in src/test/java/io/github/timhwang777/unisecret/unit/ProviderTypeTest.java
- [X] T017 [P] Create SecretManagerPropertiesTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretManagerPropertiesTest.java

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Inject Secret Value into Field (Priority: P1) MVP

**Goal**: Developers can annotate fields with @SecretValue and have secrets auto-injected at startup

**Independent Test**: Annotate a field, start application with configured provider, verify field contains expected secret value

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T018 [P] [US1] Create SecretValueAnnotationTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretValueAnnotationTest.java
- [X] T019 [P] [US1] Create SecretValueBeanPostProcessorTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretValueBeanPostProcessorTest.java
- [X] T020 [P] [US1] Create SecretResolverTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretResolverTest.java
- [X] T021 [P] [US1] Create JsonFieldExtractorTest in src/test/java/io/github/timhwang777/unisecret/unit/JsonFieldExtractorTest.java

### Implementation for User Story 1

- [X] T022 [US1] Create @SecretValue annotation with value, defaultValue, provider, providers, field, version attributes in src/main/java/io/github/timhwang777/unisecret/annotation/SecretValue.java
- [X] T023 [US1] Create JsonFieldExtractor utility for JSON field extraction in src/main/java/io/github/timhwang777/unisecret/util/JsonFieldExtractor.java
- [X] T024 [US1] Create SecretResolver service for provider chain orchestration in src/main/java/io/github/timhwang777/unisecret/provider/SecretResolver.java (depends on T013, T014)
- [X] T025 [US1] Create SecretValueBeanPostProcessor for annotation scanning and injection in src/main/java/io/github/timhwang777/unisecret/processor/SecretValueBeanPostProcessor.java (depends on T022, T024)
- [X] T026 [US1] Add audit logging for secret access (without values) in SecretValueBeanPostProcessor and SecretResolver

**Checkpoint**: User Story 1 should be fully functional - fields annotated with @SecretValue are injected at startup (requires at least one provider from US2/US3/US4)

---

## Phase 4: User Story 2 - Configure AWS Secrets Manager Provider (Priority: P1)

**Goal**: Developers can configure AWS Secrets Manager as their secret provider

**Independent Test**: Configure AWS provider, deploy secret to AWS Secrets Manager, verify application retrieves it

### Tests for User Story 2

- [X] T027 [P] [US2] Create AwsSecretProviderTest in src/test/java/io/github/timhwang777/unisecret/unit/AwsSecretProviderTest.java
- [X] T028 [P] [US2] Create AwsIntegrationTest with LocalStack in src/test/java/io/github/timhwang777/unisecret/integration/AwsIntegrationTest.java

### Implementation for User Story 2

- [X] T029 [US2] Create AwsSecretProvider implementing SecretProvider with SecretsManagerClient in src/main/java/io/github/timhwang777/unisecret/provider/AwsSecretProvider.java (depends on T013)
- [X] T030 [US2] Implement AWS version mapping (AWSCURRENT, AWSPREVIOUS) in AwsSecretProvider
- [X] T031 [US2] Add AWS-specific error handling (ResourceNotFoundException -> Optional.empty(), AccessDenied -> SecretProviderException) in AwsSecretProvider

**Checkpoint**: User Story 2 complete - AWS Secrets Manager works as a provider

---

## Phase 5: User Story 3 - Configure GCP Secret Manager Provider (Priority: P1)

**Goal**: Developers can configure GCP Secret Manager as their secret provider

**Independent Test**: Configure GCP provider, deploy secret to GCP Secret Manager, verify application retrieves it

### Tests for User Story 3

- [X] T032 [P] [US3] Create GcpSecretProviderTest in src/test/java/io/github/timhwang777/unisecret/unit/GcpSecretProviderTest.java
- [X] T033 [P] [US3] Create GcpIntegrationTest in src/test/java/io/github/timhwang777/unisecret/integration/GcpIntegrationTest.java

### Implementation for User Story 3

- [X] T034 [US3] Create GcpSecretProvider implementing SecretProvider with SecretManagerServiceClient in src/main/java/io/github/timhwang777/unisecret/provider/GcpSecretProvider.java (depends on T013)
- [X] T035 [US3] Implement GCP version mapping (latest, numeric versions) in GcpSecretProvider
- [X] T036 [US3] Add GCP-specific error handling (NotFoundException -> Optional.empty(), PermissionDenied -> SecretProviderException) in GcpSecretProvider

**Checkpoint**: User Story 3 complete - GCP Secret Manager works as a provider

---

## Phase 6: User Story 4 - Multi-Provider Fallback Chain (Priority: P1) MVP

**Goal**: Enable multiple providers simultaneously with automatic fallback

**Independent Test**: Enable multiple providers, place secret in only one, verify plugin finds it automatically

### Tests for User Story 4

- [ ] T037 [P] [US4] Create SecretResolverFallbackTest for chain behavior in src/test/java/io/github/timhwang777/unisecret/unit/SecretResolverFallbackTest.java
- [ ] T038 [P] [US4] Create MultiProviderIntegrationTest in src/test/java/io/github/timhwang777/unisecret/integration/MultiProviderIntegrationTest.java

### Implementation for User Story 4

- [ ] T039 [US4] Create LocalSecretProvider for development in src/main/java/io/github/timhwang777/unisecret/provider/LocalSecretProvider.java (depends on T013)
- [ ] T040 [US4] Implement provider chain iteration in SecretResolver with proper Optional handling
- [ ] T041 [US4] Implement annotation-level provider override (single provider via `provider` attribute)
- [ ] T042 [US4] Implement annotation-level custom chain override (via `providers` attribute)
- [ ] T043 [US4] Add detailed error message listing all attempted providers in SecretNotFoundException

### Local Provider Tests

- [ ] T044 [P] [US4] Create LocalSecretProviderTest in src/test/java/io/github/timhwang777/unisecret/unit/LocalSecretProviderTest.java
- [ ] T045 [P] [US4] Create LocalProviderIntegrationTest in src/test/java/io/github/timhwang777/unisecret/integration/LocalProviderIntegrationTest.java

**Checkpoint**: User Story 4 complete - Multi-provider fallback chain works with aws, gcp, local providers

---

## Phase 7: User Story 5 - Switch Providers via Configuration (Priority: P2)

**Goal**: Change provider order or enable/disable providers via configuration only

**Independent Test**: Change provider-order in configuration, verify fallback behavior changes accordingly

### Tests for User Story 5

- [ ] T046 [P] [US5] Create ProviderOrderConfigurationTest in src/test/java/io/github/timhwang777/unisecret/unit/ProviderOrderConfigurationTest.java
- [ ] T047 [P] [US5] Create ProfileBasedProviderTest for Spring profiles in src/test/java/io/github/timhwang777/unisecret/integration/ProfileBasedProviderTest.java

### Implementation for User Story 5

- [ ] T048 [US5] Create SecretManagerAutoConfiguration with conditional provider beans in src/main/java/io/github/timhwang777/unisecret/config/SecretManagerAutoConfiguration.java
- [ ] T049 [US5] Implement provider-order configuration binding and validation
- [ ] T050 [US5] Implement startup validation (fail if no providers enabled)
- [ ] T051 [US5] Support Spring profiles for environment-specific provider configuration

**Checkpoint**: User Story 5 complete - Providers can be switched via configuration only

---

## Phase 8: User Story 6 - Cache Retrieved Secrets (Priority: P2)

**Goal**: Cache secrets to reduce provider calls and improve performance

**Independent Test**: Retrieve same secret multiple times, verify only one provider call is made

### Tests for User Story 6

- [ ] T052 [P] [US6] Create SecretCacheTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretCacheTest.java
- [ ] T053 [P] [US6] Create CacheIntegrationTest in src/test/java/io/github/timhwang777/unisecret/integration/CacheIntegrationTest.java

### Implementation for User Story 6

- [ ] T054 [US6] Create SecretCache with Caffeine backend in src/main/java/io/github/timhwang777/unisecret/cache/SecretCache.java
- [ ] T055 [US6] Implement cache key format: {secretKey}:{version}:{field}
- [ ] T056 [US6] Implement configurable TTL and max-size from SecretManagerProperties
- [ ] T057 [US6] Integrate SecretCache with SecretResolver for cache-through pattern
- [ ] T058 [US6] Add cache statistics for monitoring

**Checkpoint**: User Story 6 complete - Secrets are cached with configurable TTL

---

## Phase 9: User Story 7 - Refresh Secrets at Runtime (Priority: P3)

**Goal**: Ability to refresh secret values at runtime without restart

**Independent Test**: Change secret value in provider, trigger refresh, verify new value is available

### Tests for User Story 7

- [ ] T059 [P] [US7] Create SecretRefreshTest in src/test/java/io/github/timhwang777/unisecret/unit/SecretRefreshTest.java
- [ ] T060 [P] [US7] Create RefreshIntegrationTest in src/test/java/io/github/timhwang777/unisecret/integration/RefreshIntegrationTest.java

### Implementation for User Story 7

- [ ] T061 [US7] Add cache invalidation methods (invalidate single, invalidateAll) to SecretCache
- [ ] T062 [US7] Create SecretRefreshService for manual refresh triggering in src/main/java/io/github/timhwang777/unisecret/config/SecretRefreshService.java
- [ ] T063 [US7] Implement scheduled refresh capability (optional, configurable)

**Checkpoint**: User Story 7 complete - Secrets can be refreshed at runtime

---

## Phase 10: Retry Logic (Cross-Cutting)

**Purpose**: Implement retry logic for transient failures

### Tests for Retry

- [ ] T064 [P] Create RetryHelperTest in src/test/java/io/github/timhwang777/unisecret/unit/RetryHelperTest.java

### Implementation

- [ ] T065 Create RetryHelper with exponential backoff (1s, 2s, 4s) in src/main/java/io/github/timhwang777/unisecret/util/RetryHelper.java
- [ ] T066 Integrate RetryHelper with SecretResolver for per-provider retries
- [ ] T067 Make retry configurable via SecretManagerProperties (max-attempts, initial-delay, multiplier, max-delay)

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements and validation

- [ ] T068 [P] Create example application.yml with all configuration options in src/main/resources/
- [ ] T069 Validate all secret access is logged without values (FR-015 compliance)
- [ ] T070 Add input validation for secret keys before provider calls
- [ ] T071 Run quickstart.md validation - ensure all examples work
- [ ] T072 Verify >80% test coverage per constitution requirement
- [ ] T073 Add Maven dependency-check plugin for vulnerability scanning

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-9)**: All depend on Foundational phase completion
  - US1 depends on: Foundational
  - US2 depends on: Foundational (can parallel with US1)
  - US3 depends on: Foundational (can parallel with US1, US2)
  - US4 depends on: Foundational, needs at least one provider (US2/US3) for full testing
  - US5 depends on: US1, US2, US3, US4 (needs providers to exist)
  - US6 depends on: US1, US4 (needs resolver to exist)
  - US7 depends on: US6 (needs cache to exist)
- **Retry Logic (Phase 10)**: Can start after Foundational, integrates with US4
- **Polish (Phase 11)**: Depends on all user stories being complete

### User Story Dependencies

```
                    ┌─────────────────┐
                    │ Setup (Phase 1) │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Foundational    │
                    │ (Phase 2)       │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
┌───────▼───────┐   ┌────────▼────────┐   ┌──────▼──────┐
│ US1: Inject   │   │ US2: AWS        │   │ US3: GCP    │
│ (Phase 3)     │   │ (Phase 4)       │   │ (Phase 5)   │
└───────┬───────┘   └────────┬────────┘   └──────┬──────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                    ┌────────▼────────┐
                    │ US4: Multi-     │
                    │ Provider Fallback│
                    │ (Phase 6)       │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ US5: Config     │
                    │ Switch (Phase 7)│
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ US6: Cache      │
                    │ (Phase 8)       │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ US7: Refresh    │
                    │ (Phase 9)       │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Polish (Phase 11)│
                    └─────────────────┘
```

### Parallel Opportunities

**Within Setup**:
- T002, T003, T004, T005 can all run in parallel

**Within Foundational**:
- T006-T010 (exceptions) can all run in parallel
- T011, T012 can run in parallel
- T015-T017 (tests) can run in parallel

**After Foundational completes**:
- US1, US2, US3 can start in parallel
- All tests marked [P] within a phase can run in parallel
- Retry Logic (Phase 10) can run in parallel with US5-US7

---

## Parallel Example: Foundational Phase

```bash
# Launch all exception classes in parallel:
Task: "Create SecretException base class"
Task: "Create SecretNotFoundException"
Task: "Create SecretProviderException"
Task: "Create SecretConfigurationException"
Task: "Create SecretParsingException"

# After exceptions complete, launch core types in parallel:
Task: "Create ProviderType enum"
Task: "Create SecretReference value object"
```

---

## Parallel Example: User Story 2 + 3 (AWS + GCP)

```bash
# After Foundational, launch AWS and GCP tests in parallel:
Task: "Create AwsSecretProviderTest"
Task: "Create GcpSecretProviderTest"

# Then implement providers in parallel:
Task: "Create AwsSecretProvider"
Task: "Create GcpSecretProvider"
```

---

## Implementation Strategy

### MVP First (User Stories 1-4)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1 (Inject)
4. Complete Phase 4: US2 (AWS) - can parallel with US1
5. Complete Phase 5: US3 (GCP) - can parallel with US1, US2
6. Complete Phase 6: US4 (Multi-Provider Fallback)
7. **STOP and VALIDATE**: Test all four P1 stories independently
8. Deploy/demo if ready - this is the MVP!

### Incremental Delivery

1. MVP: Setup + Foundational + US1-4 → Core injection with AWS/GCP/Local + fallback
2. Add US5: Configuration switching → Full configuration flexibility
3. Add US6: Caching → Production-ready performance
4. Add US7: Refresh → Operational excellence
5. Each increment adds value without breaking previous functionality

### Suggested MVP Scope

The MVP includes:
- **User Story 1**: @SecretValue annotation injection
- **User Story 2**: AWS Secrets Manager provider
- **User Story 3**: GCP Secret Manager provider
- **User Story 4**: Multi-provider fallback chain with Local provider

This delivers the core value proposition: universal secret management across cloud providers with a simple annotation-based API.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All secrets access must be logged without values (security requirement)
