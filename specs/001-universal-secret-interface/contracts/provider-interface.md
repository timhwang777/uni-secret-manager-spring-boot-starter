# Provider Interface Contract: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface
**Updated**: Multi-provider fallback chain support

## Overview

This document defines the `SecretProvider` interface contract that all provider implementations must follow, with support for the fallback chain mechanism.

---

## SecretProvider Interface

### Definition

```java
public interface SecretProvider {

    /**
     * Retrieves a secret value by key.
     * Returns Optional.empty() if secret not found (enables fallback chain).
     *
     * @param key the secret key/name in the provider
     * @return Optional containing the secret value, or empty if not found
     * @throws SecretProviderException if the provider is unavailable or returns an error
     */
    Optional<String> getSecret(String key);

    /**
     * Retrieves a specific version of a secret.
     * Returns Optional.empty() if secret or version not found (enables fallback chain).
     *
     * @param key the secret key/name in the provider
     * @param version the version identifier (provider-specific)
     * @return Optional containing the secret value, or empty if not found
     * @throws SecretProviderException if the provider is unavailable or returns an error
     */
    Optional<String> getSecret(String key, String version);

    /**
     * Returns the provider type identifier.
     *
     * @return the provider type enum value
     */
    ProviderType getProviderType();

    /**
     * Returns whether this provider is enabled.
     * Disabled providers are skipped in the fallback chain.
     *
     * @return true if provider is enabled and ready to use
     */
    boolean isEnabled();

    /**
     * Validates the provider configuration.
     * Called during application startup for enabled providers.
     *
     * @throws SecretConfigurationException if configuration is invalid
     */
    void validateConfiguration();

    /**
     * Performs cleanup when the provider is being destroyed.
     * Implementations should close clients, connections, etc.
     */
    default void destroy() {
        // Default no-op for providers that don't need cleanup
    }
}
```

**Key Design Decision**: Methods return `Optional<String>` instead of throwing `SecretNotFoundException`. This allows the `SecretResolver` to seamlessly try the next provider in the chain without catching exceptions for normal "not found" cases.

---

## ProviderType Enum

```java
public enum ProviderType {
    AWS("aws"),
    GCP("gcp"),
    LOCAL("local");

    private final String configValue;

    ProviderType(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }

    public static ProviderType fromConfigValue(String value) {
        for (ProviderType type : values()) {
            if (type.configValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider type: " + value);
    }
}
```

---

## SecretResolver (Orchestrator)

The `SecretResolver` orchestrates secret retrieval across the provider chain.

### Definition

```java
public class SecretResolver {

    private final Map<ProviderType, SecretProvider> providers;
    private final List<ProviderType> defaultProviderOrder;
    private final RetryHelper retryHelper;

    /**
     * Resolves a secret by trying providers in the specified order.
     *
     * @param reference the parsed secret reference from annotation
     * @return the secret value
     * @throws SecretNotFoundException if not found in any provider
     */
    public String resolve(SecretReference reference) {
        List<ProviderType> chain = getEffectiveChain(reference);
        List<ProviderAttempt> attempts = new ArrayList<>();

        for (ProviderType providerType : chain) {
            SecretProvider provider = providers.get(providerType);
            if (provider == null || !provider.isEnabled()) {
                continue;  // Skip disabled or missing providers
            }

            try {
                Optional<String> result = retryHelper.withRetry(
                    () -> provider.getSecret(reference.getKey(), reference.getVersion())
                );

                if (result.isPresent()) {
                    log.info("Secret '{}' found in provider '{}'", reference.getKey(), providerType);
                    return result.get();
                }

                attempts.add(new ProviderAttempt(providerType, null));  // Not found
                log.debug("Secret '{}' not found in provider '{}', trying next...",
                    reference.getKey(), providerType);

            } catch (SecretProviderException e) {
                attempts.add(new ProviderAttempt(providerType, e.getMessage()));
                log.warn("Provider '{}' failed for '{}': {}. Trying next...",
                    providerType, reference.getKey(), e.getMessage());
            }
        }

        // Exhausted all providers
        throw new SecretNotFoundException(reference.getKey(), attempts);
    }

    private List<ProviderType> getEffectiveChain(SecretReference reference) {
        if (reference.getProvider() != null) {
            return List.of(reference.getProvider());
        }
        if (reference.getProviders() != null && !reference.getProviders().isEmpty()) {
            return reference.getProviders();
        }
        return defaultProviderOrder;
    }
}
```

---

## Implementation Requirements

### 1. AWS Secrets Manager Provider

**Class**: `AwsSecretProvider`

**Behavior**:
- Uses `SecretsManagerClient` from AWS SDK v2
- Resolves credentials via default credential chain
- Maps `version` parameter to AWS stage labels
- Returns `Optional.empty()` when secret not found

**Return Value Logic**:
| AWS Response | Return Value |
|--------------|--------------|
| Secret found | `Optional.of(secretString)` |
| `ResourceNotFoundException` | `Optional.empty()` |
| `DecryptionFailureException` | throws `SecretProviderException` |
| `AccessDeniedException` | throws `SecretProviderException` |
| Network error | throws `SecretProviderException` (retryable) |

**Version Mapping**:
| Input | AWS Stage |
|-------|-----------|
| `""` or `null` | `AWSCURRENT` (default) |
| `"latest"` | `AWSCURRENT` |
| `"AWSCURRENT"` | `AWSCURRENT` |
| `"AWSPREVIOUS"` | `AWSPREVIOUS` |

---

### 2. GCP Secret Manager Provider

**Class**: `GcpSecretProvider`

**Behavior**:
- Uses `SecretManagerServiceClient` from Google Cloud client library
- Resolves credentials via Application Default Credentials (ADC)
- Constructs `SecretVersionName` from project ID, secret name, and version
- Returns `Optional.empty()` when secret not found

**Return Value Logic**:
| GCP Response | Return Value |
|--------------|--------------|
| Secret found | `Optional.of(payload.toStringUtf8())` |
| `NotFoundException` | `Optional.empty()` |
| `PermissionDeniedException` | throws `SecretProviderException` |
| `UnavailableException` | throws `SecretProviderException` (retryable) |

**Version Mapping**:
| Input | GCP Version |
|-------|-------------|
| `""` or `null` | `"latest"` |
| `"latest"` | `"latest"` |
| `"1"`, `"2"`, etc. | Specific version number |

---

### 3. Local Provider

**Class**: `LocalSecretProvider`

**Behavior**:
- Reads secrets from Spring `Environment`
- Keys are prefixed with `secrets.local.`
- Supports hierarchical keys with `/` or `.` separators
- Returns `Optional.empty()` when secret not configured

**Return Value Logic**:
| Environment Response | Return Value |
|---------------------|--------------|
| Property exists | `Optional.of(value)` |
| Property not found | `Optional.empty()` |

**Key Resolution**:
```java
public Optional<String> getSecret(String key, String version) {
    // Version ignored for local provider (log warning if specified)
    if (version != null && !version.isEmpty() && !version.equals("latest")) {
        log.warn("Version '{}' ignored for local provider (key: '{}')", version, key);
    }

    // Try multiple key formats
    String[] candidates = {
        "secrets.local." + key,
        "secrets.local." + key.replace("/", ".")
    };

    for (String candidate : candidates) {
        String value = environment.getProperty(candidate);
        if (value != null) {
            return Optional.of(value);
        }
    }

    return Optional.empty();
}
```

---

## Exception Contracts

### SecretProviderException

**When thrown**:
- Provider is unavailable (network error, timeout)
- Authentication/authorization failure
- Internal provider error

**Required fields**:
```java
public class SecretProviderException extends SecretException {
    private final String key;
    private final ProviderType providerType;
    private final boolean retryable;

    public SecretProviderException(String key, ProviderType provider,
                                    String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.key = key;
        this.providerType = provider;
        this.retryable = retryable;
    }
}
```

**Retryable vs Non-Retryable**:
| Error Type | Retryable | Example |
|------------|-----------|---------|
| Network timeout | ✅ Yes | Connection refused |
| Rate limiting | ✅ Yes | 429 Too Many Requests |
| Service unavailable | ✅ Yes | 503 from AWS/GCP |
| Authentication failure | ❌ No | Invalid credentials |
| Access denied | ❌ No | Missing IAM permissions |
| Invalid request | ❌ No | Malformed secret name |

### SecretNotFoundException

**When thrown**:
- Secret not found in ANY provider in the chain
- Only thrown by `SecretResolver`, never by individual providers

**Required fields**:
```java
public class SecretNotFoundException extends SecretException {
    private final String key;
    private final List<ProviderAttempt> attempts;

    public record ProviderAttempt(
        ProviderType provider,
        String errorMessage  // null if simply not found, error message if provider failed
    ) {}
}

// Example message
"Secret 'database/password' not found in any provider.
 Attempted providers:
   - aws (us-east-1): Not found
   - gcp (my-project): Access denied (insufficient permissions)
   - local: Not found"
```

### SecretConfigurationException

**When thrown**:
- Missing required configuration (region, project ID)
- Invalid configuration values
- Provider SDK initialization failure
- No providers enabled

---

## Thread Safety Requirements

All `SecretProvider` implementations MUST be thread-safe:

1. **Stateless operations**: `getSecret()` must not modify instance state
2. **Thread-safe clients**: Underlying SDK clients must be thread-safe
3. **No shared mutable state**: Avoid instance variables that could be modified concurrently
4. **`isEnabled()` is stable**: Value should not change after initialization

---

## Lifecycle Requirements

1. **Initialization**:
   - Provider beans created based on `secrets.{provider}.enabled`
   - `validateConfiguration()` called once during startup for enabled providers
2. **Operation**:
   - `getSecret()` called by `SecretResolver` during bean initialization and runtime refresh
   - `isEnabled()` checked before each call
3. **Shutdown**:
   - `destroy()` called during application shutdown
   - Close SDK clients, release resources

**Spring Integration**:
```java
@Bean
@ConditionalOnProperty(prefix = "secrets.aws", name = "enabled", havingValue = "true")
public SecretProvider awsSecretProvider(SecretManagerProperties properties) {
    AwsSecretProvider provider = new AwsSecretProvider(properties.getAws());
    provider.validateConfiguration();
    return provider;
}
```

---

## Extending with Custom Providers

To add a new provider (e.g., HashiCorp Vault):

1. **Add enum value** to `ProviderType`:
```java
VAULT("vault")
```

2. **Implement `SecretProvider`**:
```java
public class VaultSecretProvider implements SecretProvider {
    private final VaultTemplate vaultTemplate;
    private final boolean enabled;

    @Override
    public Optional<String> getSecret(String key) {
        return getSecret(key, null);
    }

    @Override
    public Optional<String> getSecret(String key, String version) {
        try {
            VaultResponse response = vaultTemplate.read("secret/data/" + key);
            if (response == null || response.getData() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable((String) response.getData().get("value"));
        } catch (VaultException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw new SecretProviderException(key, ProviderType.VAULT,
                e.getMessage(), isRetryable(e), e);
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.VAULT;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void validateConfiguration() {
        // Verify Vault connection
    }

    @Override
    public void destroy() {
        // Close Vault client
    }
}
```

3. **Add configuration properties**:
```java
@ConfigurationProperties(prefix = "secrets.vault")
public class VaultProperties {
    private boolean enabled = false;
    private String address;
    private String token;
    // ...
}
```

4. **Add auto-configuration**:
```java
@Bean
@ConditionalOnProperty(prefix = "secrets.vault", name = "enabled", havingValue = "true")
public SecretProvider vaultSecretProvider(SecretManagerProperties properties) {
    return new VaultSecretProvider(properties.getVault());
}
```

5. **Update configuration schema** and **provider-order** default.

---

## Testing Contract

Providers MUST pass these test scenarios:

| Scenario | Expected Result |
|----------|-----------------|
| Valid key exists | `Optional.of(value)` |
| Key does not exist | `Optional.empty()` |
| Invalid credentials | throws `SecretProviderException(retryable=false)` |
| Network timeout | throws `SecretProviderException(retryable=true)` |
| Rate limited | throws `SecretProviderException(retryable=true)` |
| Invalid version | `Optional.empty()` (treat as not found) |
| Empty key | throws `IllegalArgumentException` |
| Null key | throws `NullPointerException` |
| Provider disabled | `isEnabled()` returns `false` |

**Integration Test Pattern**:
```java
@Test
void shouldFallbackToSecondProvider() {
    // Given: AWS returns empty, GCP has the secret
    when(awsProvider.getSecret("my-key", "latest")).thenReturn(Optional.empty());
    when(gcpProvider.getSecret("my-key", "latest")).thenReturn(Optional.of("secret-value"));

    // When: Resolve with chain [aws, gcp]
    String result = resolver.resolve(SecretReference.of("my-key"));

    // Then: Returns GCP value
    assertThat(result).isEqualTo("secret-value");

    // And: AWS was tried first
    verify(awsProvider).getSecret("my-key", "latest");
    verify(gcpProvider).getSecret("my-key", "latest");
}
```
