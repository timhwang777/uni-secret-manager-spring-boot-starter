# Contract: Vault Configuration Properties

**Feature**: 002-hashicorp-vault-support
**Date**: 2026-02-25
**Type**: Spring Boot Configuration Properties

## Overview

This contract defines the configuration property interface for the HashiCorp Vault secret provider. These properties are bound via `@ConfigurationProperties(prefix = "secrets")` on the existing `SecretManagerProperties` class.

## Property Prefix: `secrets.vault`

### Connection Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.enabled` | `boolean` | `false` | Enable/disable the Vault provider |
| `secrets.vault.host` | `String` | `"localhost"` | Vault server hostname |
| `secrets.vault.port` | `int` | `8200` | Vault server port |
| `secrets.vault.scheme` | `String` | `"https"` | Connection scheme (`http` or `https`) |
| `secrets.vault.namespace` | `String` | — | Vault Enterprise namespace (optional) |

### Authentication Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.auth-method` | `enum` | `TOKEN` | Auth method: `TOKEN`, `APPROLE`, `KUBERNETES` |
| `secrets.vault.token` | `String` | — | Static Vault token (required for TOKEN auth) |

### AppRole Auth Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.app-role.role-id` | `String` | — | AppRole role ID |
| `secrets.vault.app-role.secret-id` | `String` | — | AppRole secret ID |
| `secrets.vault.app-role.path` | `String` | `"approle"` | Auth method mount path |

### Kubernetes Auth Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.kubernetes.role` | `String` | — | Kubernetes auth role name |
| `secrets.vault.kubernetes.service-account-token-path` | `String` | `/var/run/secrets/kubernetes.io/serviceaccount/token` | Path to SA JWT file |
| `secrets.vault.kubernetes.path` | `String` | `"kubernetes"` | Auth method mount path |

### KV Engine Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.mount` | `String` | `"secret"` | KV secrets engine mount path |
| `secrets.vault.kv-version` | `int` | `2` | KV engine version (1 or 2) |

### SSL Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `secrets.vault.ssl.ca-cert-path` | `String` | — | Path to PEM CA certificate file |

## Auto-Configuration Conditions

The Vault provider beans are created when ALL of the following are true:

1. `secrets.enabled=true` (or absent — matchIfMissing=true)
2. `secrets.vault.enabled=true`
3. `org.springframework.vault.core.VaultTemplate` is on the classpath

## Provider Order Integration

When Vault is enabled, include `"vault"` in the `secrets.provider-order` list:

```yaml
secrets:
  provider-order: [vault, aws, local]   # Vault tried first
```

## SecretProvider Contract Behavior

| Method | Vault Behavior |
|--------|---------------|
| `getSecret(key)` | Returns latest version from KV engine as JSON string |
| `getSecret(key, version)` | KV v2: returns specified version; KV v1: silently ignores version parameter, returns current value, logs DEBUG warning |
| `getProviderType()` | Returns `ProviderType.VAULT` |
| `validateConfiguration()` | Validates auth config, connection properties, SSL cert existence |
| `isEnabled()` | Returns `secrets.vault.enabled` |

## Error Behavior Contract

| Condition | Return/Throw | Retryable |
|-----------|-------------|-----------|
| Secret not found (404 / null) | `Optional.empty()` | N/A |
| Secret version destroyed | `Optional.empty()` | N/A |
| Secret version soft-deleted | `Optional.empty()` | N/A |
| Permission denied (403) | `SecretProviderException` | `false` |
| Vault sealed (503) | `SecretProviderException` | `true` |
| Network error | `SecretProviderException` | `true` |
| Server error (500) | `SecretProviderException` | `true` |
| Invalid request (400) | `SecretProviderException` | `false` |

## Example Configurations

### Minimal (Token auth, dev mode)

```yaml
secrets:
  provider-order: [vault]
  vault:
    enabled: true
    scheme: http
    token: my-dev-token
```

### AppRole (production)

```yaml
secrets:
  provider-order: [vault, local]
  vault:
    enabled: true
    host: vault.internal.company.com
    auth-method: APPROLE
    app-role:
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
    ssl:
      ca-cert-path: /etc/ssl/vault-ca.pem
```

### Kubernetes (container deployment)

```yaml
secrets:
  provider-order: [vault]
  vault:
    enabled: true
    host: vault.vault.svc.cluster.local
    auth-method: KUBERNETES
    kubernetes:
      role: my-app-role
    namespace: team-a
```
