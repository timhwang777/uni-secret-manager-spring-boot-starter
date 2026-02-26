# Tasks: HashiCorp Vault Secret Provider

**Input**: Design documents from `/specs/002-hashicorp-vault-support/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Included per constitution (Principle II: Test-Driven Development is mandatory).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java/io/github/timhwang777/unisecret/` and `src/test/java/io/github/timhwang777/unisecret/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add Vault dependency and extend core types to support Vault as a provider

- [X] T001 Add `spring-vault-core:3.2.0` compile dependency and `org.testcontainers:vault:1.21.4` test dependency to `pom.xml`
- [X] T002 Add `VAULT("vault")` enum constant to `src/main/java/io/github/timhwang777/unisecret/provider/ProviderType.java`
- [X] T003 Add `Vault` inner class with nested `AuthMethod` enum, `AppRole`, `Kubernetes`, and `Ssl` inner classes to `src/main/java/io/github/timhwang777/unisecret/config/SecretManagerProperties.java` — fields per `data-model.md` entity definitions
- [X] T004 Add `vault` field (`private Vault vault = new Vault()`) to the top-level `SecretManagerProperties` class in `src/main/java/io/github/timhwang777/unisecret/config/SecretManagerProperties.java` — do NOT change the default `providerOrder`; users must explicitly add `"vault"` to their `secrets.provider-order` list

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create VaultSecretProvider skeleton and auto-configuration beans — MUST complete before user stories

**CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 Create `VaultSecretProvider` class implementing `SecretProvider` in `src/main/java/io/github/timhwang777/unisecret/provider/VaultSecretProvider.java` — constructor takes `VaultTemplate`, `SecretManagerProperties.Vault`, `ObjectMapper`; stub all interface methods; `getProviderType()` returns `ProviderType.VAULT`; `isEnabled()` returns `properties.isEnabled()`
- [X] T006 Add Vault auto-configuration beans to `src/main/java/io/github/timhwang777/unisecret/config/SecretManagerAutoConfiguration.java` — create `VaultEndpoint`, `SslConfiguration`, `ClientAuthentication`, `SessionManager`, `VaultTemplate`, and `VaultSecretProvider` beans, all guarded by `@ConditionalOnProperty(prefix = "secrets.vault", name = "enabled", havingValue = "true")` and `@ConditionalOnClass(VaultTemplate.class)`

**Checkpoint**: Foundation ready — VaultSecretProvider exists as a skeleton, auto-configuration wires it up. User story implementation can now begin.

---

## Phase 3: User Story 1 - Retrieve Secrets from Vault KV v2 Engine (Priority: P1) MVP

**Goal**: Retrieve secrets from Vault's KV v2 (and v1) secrets engine via `getSecret(key)` and `getSecret(key, version)`, returning the full KV map as a JSON string.

**Independent Test**: Configure Vault with a KV v2 mount, store a secret, retrieve it via `SecretProvider.getSecret(key)` and verify the JSON-serialized map is returned.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T007 [P] [US1] Unit test: `getSecret(key)` returns JSON-serialized map from KV v2 — mock `VaultTemplate` and `VaultKeyValueOperations` in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T008 [P] [US1] Unit test: `getSecret(key, version)` returns specific version from KV v2 — test that `Version.from(n)` is passed correctly in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T009 [P] [US1] Unit test: KV v1 behavior — verify `KeyValueBackend.KV_1` is used when `kvVersion=1`; also verify `getSecret(key, "3")` with KV v1 silently ignores the version parameter and returns the current value in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T010 [P] [US1] Unit test: not-found returns `Optional.empty()` — when `VaultKeyValueOperations.get()` returns null in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T011 [P] [US1] Unit test: error mapping — 403 throws non-retryable `SecretProviderException`, 503 (sealed) throws retryable, network error throws retryable, 400 throws non-retryable in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T012 [P] [US1] Unit test: destroyed/soft-deleted version returns `Optional.empty()` in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`

### Implementation for User Story 1

- [X] T013 [US1] Implement `getSecret(key)` and `getSecret(key, version)` in `src/main/java/io/github/timhwang777/unisecret/provider/VaultSecretProvider.java` — use `vaultTemplate.opsForKeyValue(mount, KeyValueBackend.KV_2)` for v2 and `KeyValueBackend.KV_1` for v1; serialize `response.getData()` map to JSON via `objectMapper.writeValueAsString()`; map `VaultException` and `ResourceAccessException` to `SecretProviderException` with appropriate retryable flags per error mapping in `research.md` R6
- [X] T014 [US1] Integration test: start `VaultContainer` (Testcontainers), write secrets via `withSecretInVault()`, retrieve via `VaultSecretProvider.getSecret(key)` and `getSecret(key, version)` in `src/test/java/io/github/timhwang777/unisecret/integration/VaultIntegrationTest.java`

**Checkpoint**: VaultSecretProvider can retrieve secrets from Vault KV v2/v1 engine with proper error handling. Independently testable with a Vault container.

---

## Phase 4: User Story 2 - Authenticate to Vault via Multiple Methods (Priority: P2)

**Goal**: Support Token, AppRole, and Kubernetes authentication methods with configurable mount paths and automatic token renewal.

**Independent Test**: Configure each auth method independently and verify the provider authenticates and retrieves a secret.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T015 [P] [US2] Unit test: `validateConfiguration()` — token auth requires non-blank token, AppRole requires non-blank roleId and secretId, Kubernetes requires non-blank role; missing fields throw `SecretConfigurationException` in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T016 [P] [US2] Unit test: `validateConfiguration()` — valid configs for all three auth methods pass without exception in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T017 [P] [US2] Unit test: `validateConfiguration()` — when `ssl.caCertPath` is set but file does not exist, throws `SecretConfigurationException` in `src/test/java/io/github/timhwang777/unisecret/unit/VaultSecretProviderTest.java`
- [X] T018 [P] [US2] Unit test for auto-configuration: verify `ClientAuthentication` bean creation — `TokenAuthentication` for TOKEN, `AppRoleAuthentication` for APPROLE, `KubernetesAuthentication` for KUBERNETES in `src/test/java/io/github/timhwang777/unisecret/unit/VaultAutoConfigurationTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Implement `validateConfiguration()` in `src/main/java/io/github/timhwang777/unisecret/provider/VaultSecretProvider.java` — validate auth method-specific required fields, validate host not blank, validate kvVersion is 1 or 2, validate ssl.caCertPath file exists if set
- [X] T020 [US2] Implement `ClientAuthentication` factory logic in `src/main/java/io/github/timhwang777/unisecret/config/SecretManagerAutoConfiguration.java` — build `TokenAuthentication`, `AppRoleAuthentication` (with `AppRoleAuthenticationOptions` including configurable path), or `KubernetesAuthentication` (with `KubernetesAuthenticationOptions` including configurable path and JWT supplier) based on `secrets.vault.auth-method`; wrap AppRole/Kubernetes in `LifecycleAwareSessionManager` and Token in `SimpleSessionManager`
- [X] T021 [US2] Implement `SslConfiguration` bean in `src/main/java/io/github/timhwang777/unisecret/config/SecretManagerAutoConfiguration.java` — load PEM from `secrets.vault.ssl.ca-cert-path` if set, otherwise use `SslConfiguration.unconfigured()`
- [X] T022 [US2] Integration test: AppRole authentication with Testcontainers Vault — enable AppRole auth in container, create role, obtain role-id/secret-id, configure provider, verify secret retrieval in `src/test/java/io/github/timhwang777/unisecret/integration/VaultIntegrationTest.java`

**Checkpoint**: VaultSecretProvider supports all three auth methods with validation. AppRole and Kubernetes use lifecycle-aware session management for token renewal.

---

## Phase 5: User Story 3 - Use Vault in the Provider Fallback Chain (Priority: P3)

**Goal**: Vault participates in the existing provider-order fallback chain — returns `Optional.empty()` for missing secrets so subsequent providers are tried.

**Independent Test**: Configure `provider-order: [vault, local]`, store a secret only in Local, verify fallback from Vault to Local.

### Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T023 [P] [US3] Unit test: add `VAULT` test case to `ProviderType.fromConfigValue("vault")` in `src/test/java/io/github/timhwang777/unisecret/unit/ProviderTypeTest.java`
- [X] T024 [P] [US3] Unit test: add Vault properties test cases (defaults, custom values) to `src/test/java/io/github/timhwang777/unisecret/unit/SecretManagerPropertiesTest.java`

### Implementation for User Story 3

- [X] T025 [US3] Integration test: multi-provider fallback — configure `provider-order: [vault, local]`, store secret only in Local, verify resolver falls back from Vault to Local; also test Vault-first when secret exists in both in `src/test/java/io/github/timhwang777/unisecret/integration/VaultFallbackIntegrationTest.java`

**Checkpoint**: Vault correctly participates in fallback chain. Missing secrets trigger fallback to next provider.

---

## Phase 6: User Story 4 - Inject Vault Secrets via @SecretValue Annotation (Priority: P4)

**Goal**: `@SecretValue` annotation injection works with Vault-sourced secrets, including JSON field extraction.

**Independent Test**: Annotate a field with `@SecretValue(value = "my-secret", provider = "vault")` and verify the injected value matches Vault.

### Tests for User Story 4

- [X] T026 [US4] Integration test: `@SecretValue(value = "test-secret", provider = "vault")` injects the correct value; also test `field` parameter for JSON sub-field extraction with Vault in `src/test/java/io/github/timhwang777/unisecret/integration/VaultAnnotationIntegrationTest.java`

### Implementation for User Story 4

No additional implementation needed — this works automatically through the existing `SecretValueBeanPostProcessor` → `SecretResolver` → `VaultSecretProvider` chain. The integration test validates the full flow.

**Checkpoint**: All user stories are independently functional. `@SecretValue` annotation works with Vault provider.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Configuration defaults, documentation, and final validation

- [X] T027 [P] Add Vault default configuration comments to `src/main/resources/application.yml`
- [X] T028 [P] Verify all tests pass and JaCoCo coverage meets 80% minimum — run `mvn verify`
- [X] T029 Verify checkstyle and SpotBugs pass with new Vault code — run `mvn checkstyle:check spotbugs:check`
- [X] T030 Run quickstart.md validation — manually verify example configurations from `specs/002-hashicorp-vault-support/quickstart.md` work with a local Vault dev server

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 — core KV retrieval (MVP)
- **User Story 2 (Phase 4)**: Depends on Phase 2 — auth methods (can run in parallel with US1 but builds on skeleton)
- **User Story 3 (Phase 5)**: Depends on Phase 3 (US1 must be working for fallback to make sense)
- **User Story 4 (Phase 6)**: Depends on Phase 3 (US1 must be working for annotation injection to work)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2 — no dependencies on other stories
- **User Story 2 (P2)**: Can start after Phase 2 — no dependencies on other stories (auth config is independent of KV retrieval logic)
- **User Story 3 (P3)**: Depends on US1 being complete (needs working `getSecret()` for fallback testing)
- **User Story 4 (P4)**: Depends on US1 being complete (needs working `getSecret()` for annotation injection)

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD per constitution)
- Unit tests before integration tests
- Implementation after tests are in place
- Story complete before moving to next priority

### Parallel Opportunities

- T002, T003, T004 can run in parallel (Phase 1 — different files)
- T007–T012 can all run in parallel (US1 unit tests — same file but independent test methods)
- T015–T018 can all run in parallel (US2 unit tests)
- T023, T024 can run in parallel (US3 unit tests — different files)
- T027, T028 can run in parallel (Polish phase)
- US1 and US2 can be worked on in parallel after Phase 2

---

## Parallel Example: User Story 1

```bash
# Launch all unit tests for US1 together (write tests first):
Task: "T007 Unit test: getSecret(key) returns JSON-serialized map"
Task: "T008 Unit test: getSecret(key, version) returns specific version"
Task: "T009 Unit test: getSecret(key) for KV v1"
Task: "T010 Unit test: not-found returns Optional.empty()"
Task: "T011 Unit test: error mapping"
Task: "T012 Unit test: destroyed/soft-deleted version"

# Then implement (sequential):
Task: "T013 Implement getSecret methods"
Task: "T014 Integration test with Testcontainers"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T006)
3. Complete Phase 3: User Story 1 (T007–T014)
4. **STOP and VALIDATE**: Test US1 independently with `mvn test`
5. Vault provider can retrieve secrets from KV v2/v1 with token auth

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → MVP: basic Vault secret retrieval
3. Add User Story 2 → Test independently → Full auth support (AppRole, Kubernetes)
4. Add User Story 3 → Test independently → Fallback chain integration verified
5. Add User Story 4 → Test independently → Annotation injection verified
6. Polish → Full quality gates pass

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (KV retrieval)
   - Developer B: User Story 2 (auth methods)
3. After US1 complete:
   - Developer A: User Story 3 (fallback chain)
   - Developer B: User Story 4 (annotation injection)
4. Both complete Polish phase

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- TDD is mandatory per constitution — write tests first, verify they fail, then implement
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- No secrets in logs — log key names only, never values (constitution: Security-First)
