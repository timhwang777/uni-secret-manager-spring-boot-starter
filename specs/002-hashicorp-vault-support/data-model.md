# Data Model: HashiCorp Vault Secret Provider

**Feature**: 002-hashicorp-vault-support
**Date**: 2026-02-25

## Entities

### 1. ProviderType (MODIFY — enum)

Add new enum constant:

| Constant | Config Value | Description |
|----------|-------------|-------------|
| `VAULT` | `"vault"` | HashiCorp Vault KV secrets engine |

Existing constants (`AWS`, `GCP`, `LOCAL`) remain unchanged.

### 2. SecretManagerProperties.Vault (NEW — inner class)

Configuration properties bound to `secrets.vault.*` prefix.

| Field | Type | Default | Required | Description |
|-------|------|---------|----------|-------------|
| `enabled` | `boolean` | `false` | No | Master switch for Vault provider |
| `host` | `String` | `"localhost"` | When enabled | Vault server hostname |
| `port` | `int` | `8200` | No | Vault server port |
| `scheme` | `String` | `"https"` | No | Connection scheme (`http` or `https`) |
| `namespace` | `String` | `null` | No | Vault Enterprise namespace (optional) |
| `authMethod` | `AuthMethod` (enum) | `TOKEN` | No | Authentication method to use |
| `token` | `String` | `null` | When authMethod=TOKEN | Static Vault token |
| `mount` | `String` | `"secret"` | No | KV secrets engine mount path |
| `kvVersion` | `int` | `2` | No | KV engine version (`1` or `2`) |
| `appRole` | `AppRole` | new instance | No | AppRole auth configuration |
| `kubernetes` | `Kubernetes` | new instance | No | Kubernetes auth configuration |
| `ssl` | `Ssl` | new instance | No | SSL/TLS configuration |

### 3. SecretManagerProperties.Vault.AuthMethod (NEW — enum)

| Constant | Description |
|----------|-------------|
| `TOKEN` | Static token authentication |
| `APPROLE` | AppRole authentication (role-id + secret-id) |
| `KUBERNETES` | Kubernetes service account JWT authentication |

### 4. SecretManagerProperties.Vault.AppRole (NEW — inner class)

| Field | Type | Default | Required | Description |
|-------|------|---------|----------|-------------|
| `roleId` | `String` | `null` | When authMethod=APPROLE | AppRole role ID |
| `secretId` | `String` | `null` | When authMethod=APPROLE | AppRole secret ID |
| `path` | `String` | `"approle"` | No | Auth method mount path |

### 5. SecretManagerProperties.Vault.Kubernetes (NEW — inner class)

| Field | Type | Default | Required | Description |
|-------|------|---------|----------|-------------|
| `role` | `String` | `null` | When authMethod=KUBERNETES | Kubernetes auth role name |
| `serviceAccountTokenPath` | `String` | `"/var/run/secrets/kubernetes.io/serviceaccount/token"` | No | Path to service account JWT file |
| `path` | `String` | `"kubernetes"` | No | Auth method mount path |

### 6. SecretManagerProperties.Vault.Ssl (NEW — inner class)

| Field | Type | Default | Required | Description |
|-------|------|---------|----------|-------------|
| `caCertPath` | `String` | `null` | No | Path to PEM CA certificate file for TLS verification |

### 7. VaultSecretProvider (NEW — class)

Implements `SecretProvider` interface.

| Field | Type | Injected Via | Description |
|-------|------|-------------|-------------|
| `vaultTemplate` | `VaultTemplate` | Constructor | Spring Vault operations template |
| `properties` | `SecretManagerProperties.Vault` | Constructor | Vault configuration |
| `objectMapper` | `ObjectMapper` | Constructor | JSON serializer for KV map |

**State transitions**: None — the provider is stateless. Token lifecycle is managed by `SessionManager` (external to this class).

**Relationships**:
- `VaultSecretProvider` → uses `VaultTemplate` (composition)
- `VaultSecretProvider` → reads `SecretManagerProperties.Vault` (configuration)
- `VaultSecretProvider` → returns `ProviderType.VAULT` from `getProviderType()`
- `SecretResolver` → discovers `VaultSecretProvider` via `List<SecretProvider>` injection (existing pattern)

## Configuration YAML Structure

```yaml
secrets:
  provider-order: [aws, gcp, local]  # Add "vault" explicitly when enabling Vault
  vault:
    enabled: false
    host: localhost
    port: 8200
    scheme: https
    namespace:                          # Vault Enterprise namespace (optional)
    auth-method: TOKEN                  # TOKEN, APPROLE, or KUBERNETES
    token:                              # Required for TOKEN auth
    mount: secret                       # KV engine mount path
    kv-version: 2                       # 1 or 2
    app-role:
      role-id:                          # Required for APPROLE auth
      secret-id:                        # Required for APPROLE auth
      path: approle                     # Auth mount path
    kubernetes:
      role:                             # Required for KUBERNETES auth
      service-account-token-path: /var/run/secrets/kubernetes.io/serviceaccount/token
      path: kubernetes                  # Auth mount path
    ssl:
      ca-cert-path:                     # PEM CA certificate file path (optional)
```

## Validation Rules

| Rule | Scope | Error Type |
|------|-------|------------|
| `host` must not be blank when enabled | Startup | `SecretConfigurationException` |
| `kvVersion` must be 1 or 2 | Startup | `SecretConfigurationException` |
| `token` must not be blank when `authMethod=TOKEN` | Startup | `SecretConfigurationException` |
| `appRole.roleId` must not be blank when `authMethod=APPROLE` | Startup | `SecretConfigurationException` |
| `appRole.secretId` must not be blank when `authMethod=APPROLE` | Startup | `SecretConfigurationException` |
| `kubernetes.role` must not be blank when `authMethod=KUBERNETES` | Startup | `SecretConfigurationException` |
| `ssl.caCertPath` file must exist and be readable if set | Startup | `SecretConfigurationException` |
