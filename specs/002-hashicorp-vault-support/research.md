# Research: HashiCorp Vault Secret Provider

**Feature**: 002-hashicorp-vault-support
**Date**: 2026-02-25

## R1: Vault Java Client Library Selection

**Decision**: Use `spring-vault-core:3.2.0`

**Rationale**: Spring Vault 3.2.0 is the latest release compatible with Spring Boot 3.2.x / Spring Framework 6.x. It provides `VaultTemplate` (implements `VaultOperations`), built-in `ClientAuthentication` implementations for Token, AppRole, and Kubernetes, `LifecycleAwareSessionManager` for automatic token renewal, and `SslConfiguration` for custom CA certificates. This avoids implementing Vault HTTP protocol, auth flows, and token lifecycle management from scratch.

**Alternatives considered**:
- `spring-vault-core:4.0.x`: Requires Spring Framework 7 / Spring Boot 4.x — incompatible with project's Spring Boot 3.2.1
- `vault-java-driver` (BetterCloud): Third-party library; no Spring integration; no built-in session lifecycle management; less active maintenance
- Raw HTTP via `RestTemplate`: Would require implementing KV v1/v2 path logic, auth flows, token renewal, SSL configuration manually — violates KISS principle

## R2: Vault KV v2 API via Spring Vault

**Decision**: Use `VaultKeyValueOperations` from `VaultTemplate.opsForKeyValue()`

**Rationale**: Spring Vault provides a typed KV operations interface that abstracts KV v1 vs v2 API path differences:
- KV v2: `VaultTemplate.opsForKeyValue(mount, KeyValueBackend.KV_2)` — automatically prepends `/data/` to paths for reads, handles response unwrapping (`data.data`)
- KV v1: `VaultTemplate.opsForKeyValue(mount, KeyValueBackend.KV_1)` — uses direct paths
- Version-specific reads: `VaultKeyValueOperationsSupport.get(path, Version.from(n))`
- Response: `VaultResponseSupport<Map<String, Object>>` with `getData()` returning the secret map

**Key API details**:
- `get(path)` → latest version
- `get(path, Version.from(n))` → specific version (KV v2 only)
- Returns `null` when secret not found (not an exception)
- Throws `VaultException` on HTTP errors (403, 503, etc.)

## R3: Authentication Methods via Spring Vault

**Decision**: Use Spring Vault's built-in `ClientAuthentication` implementations

**Rationale**: Spring Vault provides ready-to-use classes for all three target auth methods:

1. **Token**: `TokenAuthentication(token)` — wraps a static token string
2. **AppRole**: `AppRoleAuthentication(AppRoleAuthenticationOptions)` — handles `/auth/{path}/login` with role_id + secret_id
   - `AppRoleAuthenticationOptions.builder().roleId(roleId).secretId(secretId).path(mountPath).build()`
3. **Kubernetes**: `KubernetesAuthentication(KubernetesAuthenticationOptions)` — handles `/auth/{path}/login` with JWT
   - `KubernetesAuthenticationOptions.builder().role(role).jwtSupplier(() -> readJwt(path)).path(mountPath).build()`

**Session management**:
- `LifecycleAwareSessionManager(auth, taskScheduler, endpoint)` — automatically renews tokens before expiry; suitable for AppRole and Kubernetes
- `SimpleSessionManager(auth)` — no renewal; suitable for static tokens
- Both implement `SessionManager` interface consumed by `VaultTemplate`

## R4: TLS / SSL Configuration

**Decision**: Use `SslConfiguration.forTrustStore()` with PEM file for custom CA certs

**Rationale**: Spring Vault's `SslConfiguration` supports:
- `SslConfiguration.unconfigured()` — uses JVM default truststore
- `SslConfiguration.forTrustStore(Resource)` — loads PEM or JKS file as the trust anchor
- Integrates with `ClientHttpRequestFactoryFactory` to build an SSL-configured `RestTemplate`

The `VaultEndpoint` class handles host/port/scheme configuration. Namespace support is provided via `VaultTemplate` constructor or `setNamespace()`.

## R5: Testcontainers Vault Module

**Decision**: Use `org.testcontainers:vault` module with `hashicorp/vault` Docker image

**Rationale**: Testcontainers provides a `VaultContainer` class with:
- Automatic init and unseal in dev mode
- `withVaultToken(token)` for setting the root token
- `withSecretInVault(path, data...)` for pre-populating secrets
- `withInitCommand(command)` for running Vault CLI commands at startup (e.g., enabling KV v2)

Maven coordinates: `org.testcontainers:vault:1.21.4` (matches existing testcontainers version)

The container starts Vault in dev mode (unsealed, in-memory storage) by default, which is ideal for integration tests.

## R6: Error Mapping Strategy

**Decision**: Map Vault exceptions to `SecretProviderException` with retryable flag, matching existing provider patterns

**Rationale**: Based on analysis of `AwsSecretProvider` error handling:

| Vault Error | HTTP Status | Mapping | Retryable |
|-------------|-------------|---------|-----------|
| Secret not found | 404 / null response | `Optional.empty()` | N/A |
| Permission denied | 403 | `SecretProviderException` | false |
| Vault sealed | 503 | `SecretProviderException` | true |
| Network error | N/A | `SecretProviderException` | true |
| Invalid request | 400 | `SecretProviderException` | false |
| Server error | 500 | `SecretProviderException` | true |

Spring Vault throws `VaultException` for HTTP errors. The exception message typically contains the status code. `ResourceAccessException` (Spring) wraps network-level failures.

## R7: Dependency Scope Decision

**Decision**: Add `spring-vault-core` as a compile-scope dependency (not optional)

**Rationale**: Matches the existing pattern for AWS SDK and GCP SDK — both are compile-scope dependencies in `pom.xml`. The `@ConditionalOnClass(VaultTemplate.class)` guard in auto-configuration ensures beans are not created if the dependency is excluded by the consumer. Making it optional would break the pattern and could cause confusing classpath issues for users.
