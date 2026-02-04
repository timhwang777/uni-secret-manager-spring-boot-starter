# Data Model: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface
**Updated**: Multi-provider fallback chain support

## Overview

This document defines the core entities, their attributes, and relationships for the Universal Secret Interface library. Since this is a library (not a persistence layer), entities are represented as Java classes/interfaces rather than database tables.

---

## Core Entities

### 1. SecretValue (Annotation)

**Purpose**: Marks a field for secret injection from the configured provider chain.

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | String | Yes | - | Secret key/name in the provider |
| `defaultValue` | String | No | `""` | Fallback if secret not found in any provider |
| `provider` | String | No | `""` | Single provider override (skips fallback chain) |
| `providers` | String[] | No | `{}` | Custom provider chain for this secret |
| `field` | String | No | `""` | JSON field path for structured secrets |
| `version` | String | No | `"latest"` | Secret version (GCP) or stage (AWS) |

**Validation Rules**:
- `value` must not be blank
- `field` must be valid JSON path syntax if specified (e.g., `"database.password"`)
- `provider` and `providers` are mutually exclusive; if both specified, `provider` takes precedence
- `providers` values must be valid provider types: `aws`, `gcp`, `local`

**Example Usage**:
```java
// Uses global provider-order from configuration
@SecretValue("database/credentials")
private String dbPassword;

// Uses only GCP, no fallback
@SecretValue(value = "gcp-only-secret", provider = "gcp")
private String gcpSecret;

// Custom fallback chain: try local first, then AWS
@SecretValue(value = "hybrid-secret", providers = {"local", "aws"})
private String hybridSecret;

// With JSON field extraction and default value
@SecretValue(value = "api/config", field = "apiKey", defaultValue = "dev-key")
private String apiKey;
```

---

### 2. SecretProvider (Interface)

**Purpose**: Abstraction for secret retrieval from different backends.

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getSecret` | `String key` | `Optional<String>` | Retrieves secret, empty if not found |
| `getSecret` | `String key, String version` | `Optional<String>` | Retrieves specific version |
| `getProviderType` | - | `ProviderType` | Returns the provider identifier |
| `validateConfiguration` | - | `void` | Validates provider config at startup |
| `isEnabled` | - | `boolean` | Returns whether provider is enabled |

**Note**: Methods return `Optional<String>` instead of throwing `SecretNotFoundException`. This allows the resolver to try the next provider in the chain.

**Implementations**:
- `AwsSecretProvider` - AWS Secrets Manager
- `GcpSecretProvider` - GCP Secret Manager
- `LocalSecretProvider` - Properties/YAML file (development)

**State Transitions**: N/A (stateless service)

---

### 3. SecretResolver (Service)

**Purpose**: Orchestrates secret retrieval across the provider chain.

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `resolve` | `SecretReference ref` | `String` | Resolves secret using provider chain |
| `resolve` | `String key` | `String` | Resolves using global provider order |
| `getProviderChain` | `SecretReference ref` | `List<ProviderType>` | Returns effective provider chain |

**Resolution Algorithm**:
1. Determine provider chain (annotation override or global `provider-order`)
2. Filter to only enabled providers
3. For each provider in order:
   a. Try to get secret (with retry logic)
   b. If found → return value
   c. If not found → try next provider
   d. If error → log, skip to next provider
4. If all providers exhausted → throw `SecretNotFoundException` with details

---

### 4. SecretManagerProperties (Configuration)

**Purpose**: Typed configuration for the library, bound from `application.yml`.

#### Root Properties (`secrets.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `enabled` | boolean | No | `true` | Enable/disable the library |
| `provider-order` | List<String> | No | `[aws, gcp, local]` | Fallback chain order |
| `fail-on-missing` | boolean | No | `true` | Fail startup if secret not found in any provider |

#### AWS Properties (`secrets.aws.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `enabled` | boolean | No | `false` | Enable AWS provider |
| `region` | String | No | (SDK default) | AWS region |
| `endpoint` | String | No | (SDK default) | Custom endpoint (LocalStack) |

#### GCP Properties (`secrets.gcp.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `enabled` | boolean | No | `false` | Enable GCP provider |
| `project-id` | String | No | (ADC default) | GCP project ID |
| `default-version` | String | No | `"latest"` | Default version for secrets |

#### Local Properties (`secrets.local.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `enabled` | boolean | No | `false` | Enable local provider |
| (dynamic) | String | - | - | Secret key-value pairs |

#### Cache Properties (`secrets.cache.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `enabled` | boolean | No | `true` | Enable caching |
| `ttl` | Duration | No | `5m` | Cache TTL |
| `max-size` | int | No | `1000` | Maximum cached entries |

#### Retry Properties (`secrets.retry.*`)

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `max-attempts` | int | No | `3` | Maximum retry attempts per provider |
| `initial-delay` | Duration | No | `1s` | Initial retry delay |
| `multiplier` | double | No | `2.0` | Exponential backoff multiplier |
| `max-delay` | Duration | No | `10s` | Maximum delay between retries |

---

### 5. SecretCache

**Purpose**: In-memory cache for resolved secrets (final values, not per-provider).

| Attribute | Type | Description |
|-----------|------|-------------|
| `cache` | `Cache<String, String>` | Caffeine cache instance |
| `ttl` | `Duration` | Time-to-live for entries |

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `get` | `String key, Function loader` | `String` | Get from cache or load via resolver |
| `invalidate` | `String key` | `void` | Remove specific entry |
| `invalidateAll` | - | `void` | Clear entire cache |
| `getStats` | - | `CacheStats` | Cache statistics |

**Cache Key Format**: `{secretKey}:{version}:{field}`

Note: Provider is NOT part of the cache key because the same secret key always resolves through the same chain. The final resolved value is cached regardless of which provider returned it.

---

### 6. SecretReference (Value Object)

**Purpose**: Parsed representation of a secret reference from annotation.

| Attribute | Type | Description |
|-----------|------|-------------|
| `key` | String | Secret key/name |
| `version` | String | Version identifier |
| `field` | String | JSON field path (nullable) |
| `provider` | String | Single provider override (nullable) |
| `providers` | List<String> | Custom provider chain (nullable/empty) |
| `defaultValue` | String | Fallback value (nullable) |

**Validation Rules**:
- `key` must not be null or blank
- `version` defaults to "latest" if null
- If `provider` is set, `providers` is ignored

**Effective Provider Chain Logic**:
```
if (provider != null && !provider.isEmpty()) {
    return List.of(provider);  // Single provider, no fallback
} else if (providers != null && !providers.isEmpty()) {
    return providers;  // Custom chain from annotation
} else {
    return globalProviderOrder;  // From secrets.provider-order config
}
```

---

### 7. ProviderType (Enum)

**Purpose**: Identifies the type of secret provider.

| Value | Config Value | Description |
|-------|--------------|-------------|
| `AWS` | `aws` | AWS Secrets Manager |
| `GCP` | `gcp` | GCP Secret Manager |
| `LOCAL` | `local` | Local properties/YAML |

---

## Exception Hierarchy

```
SecretException (abstract)
├── SecretNotFoundException
│   └── Thrown when secret not found in ANY provider in the chain
├── SecretProviderException
│   └── Thrown on provider communication failure (per-provider, triggers retry/fallback)
├── SecretConfigurationException
│   └── Thrown on invalid configuration (e.g., no providers enabled)
└── SecretParsingException
    └── Thrown when JSON field extraction fails
```

| Exception | Behavior | Description |
|-----------|----------|-------------|
| `SecretNotFoundException` | Fail (after exhausting chain) | Secret not in any provider |
| `SecretProviderException` | Retry then fallback | Provider unavailable |
| `SecretConfigurationException` | Fail startup | Invalid config |
| `SecretParsingException` | Fail (no fallback) | JSON parse failure |

**SecretNotFoundException Details**:
```java
public class SecretNotFoundException extends SecretException {
    private final String key;
    private final List<ProviderAttempt> attempts;  // What was tried

    public record ProviderAttempt(
        ProviderType provider,
        String errorMessage  // null if simply not found
    ) {}
}
```

---

## Relationships

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      SecretManagerAutoConfiguration                       │
│                                                                          │
│  ┌─────────────────┐                                                     │
│  │ SecretManager   │                                                     │
│  │ Properties      │──────────────────────────────────┐                  │
│  └─────────────────┘                                  │                  │
│          │                                            ▼                  │
│          │ provider-order              ┌──────────────────────────┐      │
│          │                             │   SecretProvider (I)     │      │
│          ▼                             └──────────────────────────┘      │
│  ┌─────────────────┐                              │                      │
│  │ SecretResolver  │──────────────────────────────┼── AwsSecretProvider  │
│  │                 │  iterates chain              ├── GcpSecretProvider  │
│  └─────────────────┘                              └── LocalSecretProvider│
│          │                                                               │
│          │ delegates                                                     │
│          ▼                                                               │
│  ┌─────────────────┐    ┌──────────────────────────────┐                │
│  │ SecretCache     │<───│ SecretValueBeanPostProcessor │                │
│  │ (Caffeine)      │    └──────────────────────────────┘                │
│  └─────────────────┘                    │                               │
│                                         │ parses                        │
│                                         ▼                               │
│                              ┌──────────────────┐                       │
│                              │ SecretReference  │                       │
│                              │ (value object)   │                       │
│                              └──────────────────┘                       │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key Relationships**:
1. `SecretManagerAutoConfiguration` creates all enabled provider beans
2. `SecretResolver` receives the provider-order from properties and all provider beans
3. `SecretValueBeanPostProcessor` scans beans for `@SecretValue` annotations
4. Processor parses annotation into `SecretReference`
5. Cache delegates to `SecretResolver` on cache miss
6. `SecretResolver` iterates through the effective provider chain until found

---

## Configuration Examples

### Multi-Provider with Fallback (Production)

```yaml
secrets:
  provider-order:
    - aws      # Try AWS first
    - gcp      # Fallback to GCP
  fail-on-missing: true

  aws:
    enabled: true
    region: us-east-1

  gcp:
    enabled: true
    project-id: my-project

  cache:
    enabled: true
    ttl: 5m

  retry:
    max-attempts: 3
    initial-delay: 1s
    multiplier: 2.0
```

### Single Provider (Simple Setup)

```yaml
secrets:
  provider-order:
    - aws
  fail-on-missing: true

  aws:
    enabled: true
    region: us-east-1
```

### Local Development with Fallback

```yaml
secrets:
  provider-order:
    - local    # Try local first (fast, no network)
    - aws      # Fallback to AWS for secrets not in local
  fail-on-missing: false  # Allow missing in dev

  local:
    enabled: true
    database-password: dev-password
    api-key: dev-api-key

  aws:
    enabled: true
    region: us-east-1
    endpoint: http://localhost:4566  # LocalStack
```

### Hybrid Cloud (Multi-Region)

```yaml
secrets:
  provider-order:
    - gcp      # Primary: GCP (our main cloud)
    - aws      # Secondary: AWS (legacy secrets)
    - local    # Fallback: local overrides

  gcp:
    enabled: true
    project-id: production-project

  aws:
    enabled: true
    region: us-west-2

  local:
    enabled: true
    feature-flag: enabled  # Local override
```
