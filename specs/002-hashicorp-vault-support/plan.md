# Implementation Plan: HashiCorp Vault Secret Provider

**Branch**: `002-hashicorp-vault-support` | **Date**: 2026-02-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-hashicorp-vault-support/spec.md`

## Summary

Add HashiCorp Vault as a new secret provider to the universal secret manager, implementing the existing `SecretProvider` interface. The provider uses Spring Vault (`spring-vault-core:3.2.0`) to connect to Vault's KV v1/v2 secrets engine with support for Token, AppRole, and Kubernetes authentication methods. The implementation follows the established adapter pattern used by `AwsSecretProvider` and `GcpSecretProvider` — a new `VaultSecretProvider` class, a `Vault` properties inner class, auto-configuration beans with conditional class/property guards, and corresponding unit/integration tests.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.2.1, spring-vault-core 3.2.0, jackson-databind (existing)
**Storage**: N/A (client-side library; Vault server is external)
**Testing**: JUnit 5 + Mockito + AssertJ (unit), Testcontainers with `hashicorp/vault` image (integration)
**Target Platform**: JVM — Spring Boot applications
**Project Type**: Single library (Spring Boot starter)
**Performance Goals**: Governed by existing cache and retry configuration; no additional targets
**Constraints**: Must not modify existing provider implementations; optional dependency (classpath guard)
**Scale/Scope**: ~6 new/modified production files, ~4 new test files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Security-First | PASS | TLS 1.2+ default scheme; PEM CA cert support (FR-012); no secrets in logs (SLF4J with key-only logging, matching existing providers); token renewal prevents credential staleness |
| II. Test-Driven Development | PASS | Unit tests (Mockito) + integration tests (Testcontainers Vault) planned; >80% coverage enforced by existing JaCoCo rule |
| III. API-First | PASS | Library exposes no REST endpoints; provider implements existing `SecretProvider` contract; configuration properties documented in quickstart.md |
| IV. Spring Best Practices | PASS | Constructor injection; `@ConfigurationProperties` for externalized config; `@ConditionalOnClass` + `@ConditionalOnProperty` for auto-config; matches existing patterns exactly |
| V. KISS Principle | PASS | Single `VaultSecretProvider` class wrapping `VaultTemplate`; reuses Spring Vault's built-in auth and session management; no custom abstractions |
| VI. Extensibility | PASS | Adds `VAULT` to `ProviderType` enum; new `Vault` inner class in properties; config-driven auth method selection; follows existing adapter pattern |

**Security Requirements Check**:
- Authentication: Token, AppRole, Kubernetes auth methods (FR-005/006/007)
- Data Protection: TLS 1.2+ in transit (FR-012); at-rest encryption delegated to Vault server
- Audit Logging: Secret access logged at DEBUG level (key only, never value) — matches existing provider pattern
- Input Validation: Auth method enum validation; required property checks in `validateConfiguration()`
- Dependency Management: `spring-vault-core` is a well-maintained Spring project with regular security updates

## Project Structure

### Documentation (this feature)

```text
specs/002-hashicorp-vault-support/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── vault-config-properties.md
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/java/io/github/timhwang777/unisecret/
│   ├── config/
│   │   ├── SecretManagerAutoConfiguration.java  # MODIFY: add Vault beans
│   │   └── SecretManagerProperties.java         # MODIFY: add Vault inner class
│   └── provider/
│       ├── ProviderType.java                    # MODIFY: add VAULT enum value
│       └── VaultSecretProvider.java             # NEW: Vault provider implementation
├── main/resources/
│   └── application.yml                          # MODIFY: add vault defaults
└── test/java/io/github/timhwang777/unisecret/
    ├── unit/
    │   ├── VaultSecretProviderTest.java          # NEW: unit tests
    │   └── ProviderTypeTest.java                 # MODIFY: add VAULT test case
    └── integration/
        └── VaultIntegrationTest.java             # NEW: Testcontainers integration test
```

**Structure Decision**: Follows existing single-project layout. New files are placed in the same packages as their counterparts (e.g., `VaultSecretProvider` alongside `AwsSecretProvider` in `provider/`). No new packages or modules needed.

## Complexity Tracking

No constitution violations. No complexity justifications needed.

## Design Decisions

### D1: Use Spring Vault (`spring-vault-core`) as the Vault Client

**Decision**: Use `spring-vault-core:3.2.0` rather than raw HTTP calls or the Vault Java Driver.

**Rationale**: Spring Vault provides `VaultTemplate` (thread-safe), built-in `ClientAuthentication` implementations for all target auth methods (Token, AppRole, Kubernetes), `LifecycleAwareSessionManager` for automatic token renewal, and `SslConfiguration` for PEM CA cert support. It integrates naturally with Spring Boot and avoids reinventing session management.

**Alternatives rejected**:
- Raw HTTP (`RestTemplate`/`WebClient`): Would require implementing auth flows, token renewal, KV v1/v2 path logic manually. Violates KISS.
- `vault-java-driver` (BetterCloud): Not a Spring project; no lifecycle-aware session management; less maintained.

### D2: KV Map Serialization Strategy

**Decision**: Always serialize the full Vault KV data map as a JSON string using Jackson `ObjectMapper`.

**Rationale**: Clarified in spec (Session 2026-02-25). Provides consistent behavior regardless of key count. Works with the existing `JsonFieldExtractor` for sub-field extraction via `@SecretValue(field = "...")`.

### D3: Auth Method Selection via Enum Configuration

**Decision**: Use a `secrets.vault.auth-method` property with an enum (`TOKEN`, `APPROLE`, `KUBERNETES`) to select the authentication strategy at configuration time.

**Rationale**: Matches the configuration-driven pattern used throughout the project. Each auth method has distinct required properties, validated in `validateConfiguration()`. Default: `TOKEN` (simplest, works for development and CI).

### D4: Vault Dependency as Compile-Scope with @ConditionalOnClass Guard

**Decision**: Add `spring-vault-core` as a compile dependency (same as AWS SDK and GCP SDK), guarded by `@ConditionalOnClass(VaultTemplate.class)` in auto-configuration.

**Rationale**: Matches the existing pattern — AWS and GCP SDKs are also compile-scope dependencies. Users who don't use Vault can exclude the dependency if needed, and the auto-config will not activate without the class on the classpath.

### D5: Testcontainers Vault for Integration Tests

**Decision**: Use `testcontainers/vault` module with the `hashicorp/vault` Docker image.

**Rationale**: Follows the established Testcontainers pattern (already used for LocalStack/AWS). The `vault` Testcontainers module provides `VaultContainer` with helper methods for `initAndUnseal()` and writing secrets.

## Implementation Approach

### Step 1: Extend Core Types (FR-001)

- Add `VAULT("vault")` to `ProviderType` enum
- Add `Vault` inner class to `SecretManagerProperties`
- Do NOT change the default `providerOrder`. Users must explicitly add `"vault"` to their `providerOrder` when enabling Vault. The `@ConditionalOnProperty` guard ensures Vault beans are only created when `secrets.vault.enabled=true`, but the provider is only queried during resolution if it appears in `providerOrder`.

### Step 2: Implement VaultSecretProvider (FR-002 through FR-004, FR-010, FR-011)

- Constructor: `VaultSecretProvider(VaultTemplate vaultTemplate, SecretManagerProperties.Vault properties, ObjectMapper objectMapper)`
- `getSecret(key)`: delegates to `getSecret(key, null)` (latest version)
- `getSecret(key, version)`: uses `VaultKeyValueOperations` for KV v2 or `VaultKeyValueOperations` for KV v1
  - KV v2: `vaultTemplate.opsForKeyValue(mount, KeyValueBackend.KV_2).get(key)` with optional `Version.from(version)`
  - KV v1: `vaultTemplate.opsForKeyValue(mount, KeyValueBackend.KV_1).get(key)`
  - Serialize `response.getData()` map to JSON string via `objectMapper.writeValueAsString()`
- Error mapping follows `AwsSecretProvider` pattern:
  - `VaultException` with 404 status → `Optional.empty()`
  - `VaultException` with 403 → `SecretProviderException(msg, cause, false)` (non-retryable)
  - `VaultException` with 503 (sealed) → `SecretProviderException(msg, cause, true)` (retryable)
  - `ResourceAccessException` (network) → `SecretProviderException(msg, cause, true)` (retryable)

### Step 3: Authentication Configuration (FR-005 through FR-007, FR-014)

- Build `ClientAuthentication` based on `secrets.vault.auth-method`:
  - `TOKEN`: `TokenAuthentication(token)`
  - `APPROLE`: `AppRoleAuthentication` with `AppRoleAuthenticationOptions` (roleId, secretId, configurable path)
  - `KUBERNETES`: `KubernetesAuthentication` with `KubernetesAuthenticationOptions` (role, jwtSupplier, configurable path)
- Wrap in `LifecycleAwareSessionManager` for AppRole/Kubernetes (handles token renewal)
- Use `SimpleSessionManager` for static token (no renewal needed)

### Step 4: Auto-Configuration (FR-008, FR-012, FR-013)

- Add Vault beans to `SecretManagerAutoConfiguration`:
  - `VaultEndpoint` bean: configured from `secrets.vault.host`, `port`, `scheme`
  - `SslConfiguration` bean: from `secrets.vault.ssl.ca-cert-path` (PEM) or `SslConfiguration.unconfigured()` if absent
  - `ClientAuthentication` bean: factory method based on `auth-method`
  - `SessionManager` bean: `LifecycleAwareSessionManager` or `SimpleSessionManager`
  - `VaultTemplate` bean: from endpoint, SSL config, session manager
  - `VaultSecretProvider` bean: from VaultTemplate, properties, ObjectMapper
- All Vault beans guarded by `@ConditionalOnProperty(prefix = "secrets.vault", name = "enabled", havingValue = "true")` and `@ConditionalOnClass(VaultTemplate.class)`

### Step 5: Configuration Validation (FR-009)

- `validateConfiguration()` checks:
  - `auth-method` is set and valid
  - For TOKEN: `token` must not be blank
  - For APPROLE: `role-id` and `secret-id` must not be blank
  - For KUBERNETES: `role` must not be blank
  - `host` must not be blank
  - If `ssl.ca-cert-path` is set, file must exist and be readable
  - Optionally: attempt a health check (`sys/health` endpoint) and log result

### Step 6: Tests

**Unit tests** (`VaultSecretProviderTest`):
- Mock `VaultTemplate` and `VaultKeyValueOperations`
- Test `getSecret(key)` returns JSON-serialized map
- Test `getSecret(key, version)` passes version correctly
- Test not-found → `Optional.empty()`
- Test auth failure → `SecretProviderException(retryable=false)`
- Test network error → `SecretProviderException(retryable=true)`
- Test sealed vault → `SecretProviderException(retryable=true)`
- Test KV v1 vs v2 path selection
- Test `validateConfiguration()` for each auth method

**Integration tests** (`VaultIntegrationTest`):
- Testcontainers `VaultContainer` with KV v2 engine
- Write secrets via Vault CLI in container, read via provider
- Test version retrieval
- Test fallback chain with Vault + Local
