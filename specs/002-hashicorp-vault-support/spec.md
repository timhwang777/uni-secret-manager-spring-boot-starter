# Feature Specification: HashiCorp Vault Secret Provider

**Feature Branch**: `002-hashicorp-vault-support`
**Created**: 2026-02-25
**Status**: Draft
**Input**: User description: "add hashicorp vault support, in order to match the latest api, you may need to research the official documents"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retrieve Secrets from Vault KV v2 Engine (Priority: P1)

As a developer using the uni-secret-manager library, I want to retrieve secrets stored in HashiCorp Vault's KV v2 secrets engine so that I can use Vault as my secret management backend alongside existing providers (AWS, GCP, Local).

**Why this priority**: This is the core functionality — without reading secrets from Vault, all other Vault-related features are meaningless. It delivers immediate value by adding a new provider to the existing fallback chain.

**Independent Test**: Can be fully tested by configuring a Vault provider with a KV v2 mount, storing a secret, and retrieving it via `SecretProvider.getSecret(key)`. Delivers the ability to use Vault as a drop-in secret backend.

**Acceptance Scenarios**:

1. **Given** Vault is configured with `secrets.vault.enabled=true` and a valid token, **When** `getSecret("my-secret")` is called, **Then** the latest version of the secret value is returned from the configured KV v2 mount path.
2. **Given** Vault is configured and the secret has multiple versions, **When** `getSecret("my-secret", "3")` is called, **Then** version 3 of the secret is returned.
3. **Given** Vault is configured, **When** `getSecret("nonexistent-key")` is called, **Then** `Optional.empty()` is returned (no exception thrown).
4. **Given** Vault is configured but the token is invalid or expired, **When** `getSecret("my-secret")` is called, **Then** a `SecretProviderException` is thrown with a descriptive error message.

---

### User Story 2 - Authenticate to Vault via Multiple Methods (Priority: P2)

As a developer deploying applications across different environments, I want to authenticate to Vault using different methods (static token, AppRole, Kubernetes) so that I can use the appropriate authentication strategy for each deployment context.

**Why this priority**: Authentication is essential for any Vault interaction, but token-based auth (covered in P1) is sufficient for initial use. AppRole and Kubernetes auth expand deployment flexibility and are critical for production use.

**Independent Test**: Can be tested by configuring each auth method independently and verifying that the provider successfully authenticates and retrieves a secret.

**Acceptance Scenarios**:

1. **Given** `secrets.vault.auth-method=TOKEN` and a valid `secrets.vault.token` is provided, **When** the application starts, **Then** the Vault provider authenticates using the static token.
2. **Given** `secrets.vault.auth-method=APPROLE` with valid `role-id` and `secret-id`, **When** the application starts, **Then** the provider authenticates via the AppRole login endpoint and obtains a client token.
3. **Given** `secrets.vault.auth-method=KUBERNETES` with a valid role and service account token, **When** the application runs inside a Kubernetes pod, **Then** the provider authenticates using the pod's service account JWT.
4. **Given** an invalid auth method configuration, **When** the application starts, **Then** `validateConfiguration()` fails with a clear error message indicating the misconfiguration.

---

### User Story 3 - Use Vault in the Provider Fallback Chain (Priority: P3)

As a developer using multiple secret providers, I want Vault to participate in the existing provider-order fallback chain so that I can combine Vault with other providers (e.g., Vault as primary, Local as fallback for development).

**Why this priority**: Fallback chain integration is what makes the library valuable as a "universal" secret manager. However, this should work largely out of the box once the Vault provider implements the `SecretProvider` interface correctly.

**Independent Test**: Can be tested by configuring `secrets.provider-order: [vault, local]`, storing a secret only in Local, and verifying that the resolver falls back from Vault to Local.

**Acceptance Scenarios**:

1. **Given** `secrets.provider-order: [vault, local]` and the secret exists in Vault, **When** `getSecret("my-secret")` is called, **Then** the value from Vault is returned.
2. **Given** `secrets.provider-order: [vault, local]` and the secret does NOT exist in Vault but exists in Local, **When** `getSecret("my-secret")` is called, **Then** the value from Local is returned (fallback).
3. **Given** `secrets.provider-order: [vault, aws]` and Vault is temporarily unavailable, **When** the retry policy is exhausted, **Then** the resolver falls back to AWS.

---

### User Story 4 - Inject Vault Secrets via @SecretValue Annotation (Priority: P4)

As a developer, I want to use the existing `@SecretValue` annotation to inject Vault-sourced secrets into my Spring beans so that I have a consistent developer experience across all providers.

**Why this priority**: Annotation-based injection is a convenience layer that builds on top of the core provider. It should work automatically once the provider is registered, but needs verification.

**Independent Test**: Can be tested by annotating a field with `@SecretValue(value = "my-secret", provider = "vault")` and verifying the injected value matches what is stored in Vault.

**Acceptance Scenarios**:

1. **Given** a Spring bean with `@SecretValue(value = "db-password", provider = "vault")`, **When** the bean is initialized, **Then** the field is populated with the secret from Vault.
2. **Given** `@SecretValue(value = "config", field = "database.host")` and the Vault secret contains a JSON object, **When** the bean is initialized, **Then** the nested JSON field value is extracted and injected.

---

### Edge Cases

- What happens when the Vault server is sealed? The provider should throw a `SecretProviderException` indicating the Vault is sealed (HTTP 503).
- What happens when the KV mount path does not exist? The provider should throw a `SecretProviderException` with a clear message about the invalid mount path.
- What happens when a requested secret version has been destroyed (permanently deleted)? The provider should return `Optional.empty()`.
- What happens when a requested secret version has been soft-deleted? The provider should return `Optional.empty()`.
- What happens when the Vault token expires mid-session? The session manager should handle token renewal automatically; if renewal fails, the next secret retrieval should throw a `SecretProviderException`.
- What happens when Vault returns a secret with multiple key-value pairs? The provider should return the full JSON string, allowing the existing JSON field extractor to handle sub-field extraction.
- What happens when using KV v1 instead of v2? The provider should support configurable KV version and adjust API paths accordingly.
- What happens when `getSecret(key, version)` is called against KV v1? The provider should silently ignore the version parameter and return the current value, since KV v1 does not support versioning. A DEBUG-level log message should note that versioning is not supported for KV v1.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support HashiCorp Vault as a secret provider type alongside existing AWS, GCP, and Local providers.
- **FR-002**: System MUST retrieve secrets from Vault's KV v2 secrets engine, returning the latest version by default. The full KV map MUST be serialized as a JSON string (e.g., `{"username":"admin","password":"s3cret"}`), regardless of how many keys the map contains. Individual values can be extracted using the existing `field` parameter.
- **FR-003**: System MUST support retrieving a specific version of a secret from Vault's KV v2 engine by version number.
- **FR-004**: System MUST support KV v1 secrets engine as an alternative to v2, configurable via properties.
- **FR-005**: System MUST support token-based authentication to Vault.
- **FR-006**: System MUST support AppRole authentication to Vault (role-id + secret-id), with a configurable auth mount path (default: `approle`).
- **FR-007**: System MUST support Kubernetes authentication to Vault (service account JWT), with a configurable auth mount path (default: `kubernetes`).
- **FR-008**: System MUST auto-configure the Vault provider when `secrets.vault.enabled=true` and the Vault client library is on the classpath.
- **FR-009**: System MUST validate Vault configuration at startup (connectivity, authentication, mount path accessibility) via `validateConfiguration()`.
- **FR-010**: System MUST return `Optional.empty()` when a secret is not found, deleted, or destroyed in Vault, consistent with existing provider behavior.
- **FR-011**: System MUST throw `SecretProviderException` for Vault communication errors, authentication failures, and sealed Vault states.
- **FR-012**: System MUST support configurable Vault connection properties: host, port, scheme (HTTP/HTTPS), namespace (for Vault Enterprise), and an optional PEM CA certificate file path for TLS verification against Vault servers using self-signed or internal CA certificates.
- **FR-013**: System MUST support configurable KV secrets engine mount path (default: `secret`).
- **FR-014**: System MUST handle Vault token lifecycle (renewal) automatically when using session-aware authentication methods (AppRole, Kubernetes).
- **FR-015**: System MUST integrate with the existing provider-order fallback chain, cache, and retry mechanisms without modification to those systems.

### Key Entities

- **Vault Provider Configuration**: Connection details (host, port, scheme), authentication method and credentials, KV engine version, mount path, and optional namespace.
- **Vault Authentication Session**: An authenticated Vault token obtained via the configured auth method, with automatic renewal for time-limited tokens.
- **KV Secret**: A versioned key-value pair stored in Vault's KV secrets engine, where each version has metadata (creation time, deletion status, destroyed status).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can retrieve secrets from Vault using the same `SecretProvider` interface used for AWS, GCP, and Local providers, with no changes to consuming code.
- **SC-002**: The Vault provider supports at least 3 authentication methods (Token, AppRole, Kubernetes) to cover common deployment environments.
- **SC-003**: The Vault provider introduces no additional latency beyond the Vault server round-trip — no client-side processing overhead beyond JSON serialization of the KV map. Performance is governed by the existing cache and retry configuration (no Vault-specific tuning required).
- **SC-004**: The Vault provider correctly participates in the fallback chain — returning `Optional.empty()` for missing secrets so subsequent providers are tried.
- **SC-005**: Configuration validation at startup detects and reports misconfigurations (bad credentials, unreachable server, invalid mount path) before the application processes requests.
- **SC-006**: The Vault provider handles token renewal automatically, allowing long-running applications to maintain Vault connectivity without manual intervention.

## Clarifications

### Session 2026-02-25

- Q: How should Vault KV map secrets be mapped to `Optional<String>`? → A: Always serialize the full KV map as a JSON string, regardless of key count. Use existing `field` parameter for individual value extraction.
- Q: Should the provider support custom TLS certificates for Vault connections? → A: Yes, support a configurable PEM CA certificate file path (similar to Vault's `VAULT_CACERT`).
- Q: Should auth methods support configurable mount paths? → A: Yes, support configurable auth mount paths per method, defaulting to standard paths (`approle`, `kubernetes`).

## Assumptions

- The Vault server is externally managed; this library is a client-side integration only.
- KV v2 is the default and recommended secrets engine version; KV v1 is supported as a secondary option.
- The Spring Vault library (`spring-vault-core`) is used as the underlying Vault client, leveraging its built-in session management and authentication support.
- Vault Enterprise features (namespaces) are supported via optional configuration but are not required for core functionality.
- Transit secrets engine, dynamic secrets, and other Vault capabilities beyond KV are out of scope for this feature.
- TLS/HTTPS is the default connection scheme; HTTP is supported for development/testing environments.

## Out of Scope

- Vault server provisioning, setup, or administration.
- Transit secrets engine (encryption-as-a-service).
- Dynamic secrets (database credentials, AWS IAM, etc.).
- Vault Agent integration (sidecar-based secret injection).
- Vault UI or CLI interactions.
- Multi-namespace secret aggregation.
