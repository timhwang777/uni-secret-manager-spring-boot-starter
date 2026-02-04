# Annotation API Contract: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface
**Updated**: Multi-provider fallback chain support

## Overview

This document defines the annotation-based API contract for injecting secrets into Spring beans with multi-provider fallback support.

---

## @SecretValue Annotation

### Definition

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecretValue {

    /**
     * The secret key/name in the provider.
     * Required.
     *
     * @return the secret key
     */
    String value();

    /**
     * Default value if secret is not found in any provider and fail-on-missing is false.
     * Optional. Defaults to empty string (meaning no default).
     *
     * @return the default value
     */
    String defaultValue() default "";

    /**
     * Single provider override - uses only this provider, no fallback.
     * Optional. If empty, uses global provider-order from configuration.
     * Valid values: "aws", "gcp", "local"
     *
     * @return the provider identifier
     */
    String provider() default "";

    /**
     * Custom provider chain for this secret.
     * Optional. If empty, uses global provider-order from configuration.
     * Valid values: "aws", "gcp", "local"
     * Ignored if 'provider' is specified.
     *
     * @return array of provider identifiers in fallback order
     */
    String[] providers() default {};

    /**
     * JSON field path for extracting a value from a structured secret.
     * Optional. Uses dot notation (e.g., "database.password").
     *
     * @return the JSON field path
     */
    String field() default "";

    /**
     * Secret version identifier.
     * For GCP: version number (e.g., "1", "2") or "latest"
     * For AWS: stage label (e.g., "AWSCURRENT", "AWSPREVIOUS")
     * Optional. Defaults to "latest" / "AWSCURRENT".
     *
     * @return the version identifier
     */
    String version() default "";
}
```

---

## Usage Patterns

### Basic Usage (Uses Global Provider Order)

```java
@Service
public class MyService {

    // Uses secrets.provider-order from configuration
    // Tries each enabled provider until found
    @SecretValue("database/password")
    private String dbPassword;

    @SecretValue("api-key")
    private String apiKey;
}
```

### Single Provider Override (No Fallback)

```java
@Service
public class CloudSpecificService {

    // Only uses AWS - no fallback to other providers
    @SecretValue(value = "aws-only-secret", provider = "aws")
    private String awsSecret;

    // Only uses GCP - no fallback to other providers
    @SecretValue(value = "gcp-only-secret", provider = "gcp")
    private String gcpSecret;
}
```

### Custom Provider Chain

```java
@Service
public class HybridService {

    // Custom fallback: try local first, then AWS
    // Ignores global provider-order for this secret
    @SecretValue(value = "hybrid-secret", providers = {"local", "aws"})
    private String hybridSecret;

    // Custom fallback: GCP primary, AWS backup
    @SecretValue(value = "multi-cloud-secret", providers = {"gcp", "aws"})
    private String multiCloudSecret;
}
```

### With Default Value

```java
@Service
public class MyService {

    // If not found in any provider, use default (when fail-on-missing=false)
    @SecretValue(value = "optional-feature-flag", defaultValue = "disabled")
    private String featureFlag;
}
```

### JSON Field Extraction

For secrets stored as JSON:
```json
{
  "username": "admin",
  "password": "secret123",
  "host": "db.example.com"
}
```

```java
@Service
public class DatabaseConfig {

    // All three fields come from the same secret
    // Provider chain is tried once; field extraction happens after retrieval
    @SecretValue(value = "database/credentials", field = "username")
    private String username;

    @SecretValue(value = "database/credentials", field = "password")
    private String password;

    @SecretValue(value = "database/credentials", field = "host")
    private String host;
}
```

### Version Specification

```java
@Service
public class VersionedSecretService {

    // Use latest version (default)
    @SecretValue("rotating-secret")
    private String latestSecret;

    // Use specific version (GCP)
    @SecretValue(value = "pinned-secret", version = "5")
    private String pinnedSecret;

    // Use previous version (AWS)
    @SecretValue(value = "rollback-secret", version = "AWSPREVIOUS")
    private String previousSecret;
}
```

### Combined: Custom Chain + JSON Field + Default

```java
@Service
public class ComplexService {

    // Try local first (for dev overrides), then GCP
    // Extract 'apiKey' field from JSON
    // Default to 'dev-key' if not found anywhere
    @SecretValue(
        value = "api/config",
        providers = {"local", "gcp"},
        field = "apiKey",
        defaultValue = "dev-key"
    )
    private String apiKey;
}
```

---

## Provider Resolution Logic

The effective provider chain is determined in this order:

1. **`provider` attribute set** → Single provider, no fallback
2. **`providers` array set** → Custom chain from annotation
3. **Neither set** → Global `secrets.provider-order` from configuration

```java
// Pseudo-code
List<ProviderType> getEffectiveChain(SecretValue annotation, Properties config) {
    if (!annotation.provider().isEmpty()) {
        return List.of(ProviderType.fromString(annotation.provider()));
    }
    if (annotation.providers().length > 0) {
        return Arrays.stream(annotation.providers())
            .map(ProviderType::fromString)
            .toList();
    }
    return config.getProviderOrder();  // Global default
}
```

---

## Supported Field Types

| Type | Support | Notes |
|------|---------|-------|
| `String` | ✅ Full | Primary use case |
| `char[]` | ✅ Full | Security-conscious alternative |
| `byte[]` | ❌ Not supported | Binary secrets out of scope |
| Custom objects | ❌ Not supported | Use JSON field extraction |

---

## Injection Timing

Secrets are injected during Spring bean initialization, specifically in `BeanPostProcessor.postProcessBeforeInitialization()`.

**Lifecycle**:
1. Bean instantiation
2. **Secret injection** ← `@SecretValue` fields populated here (full chain resolution)
3. `@PostConstruct` methods
4. Bean ready for use

This ensures `@SecretValue` fields are available in `@PostConstruct` methods.

---

## Error Handling

### Secret Not Found (Exhausted All Providers)

When a secret is not found in any provider in the chain:

```
Behavior depends on secrets.fail-on-missing configuration:
- true (default): Application fails to start with SecretNotFoundException
- false: Field is left null (or default value if specified)
```

**Error Message Format** (lists all attempted providers):
```
SecretNotFoundException: Secret 'database/password' not found in any provider.
  Attempted providers:
    - aws (us-east-1): Not found
    - gcp (my-project): Not found
    - local: Not found
  Suggestion: Verify the secret exists in at least one enabled provider
```

### Provider Error (Triggers Fallback)

When a provider throws an error (not "not found"):

```
INFO  SecretResolver : Provider 'aws' failed for 'database/password': Access denied. Trying next provider...
INFO  SecretResolver : Provider 'gcp' succeeded for 'database/password'
```

The error is logged, and the next provider in the chain is tried.

### JSON Field Not Found

When extracting a field from JSON and the field path doesn't exist:

```
SecretParsingException: Field 'database.port' not found in secret 'database/credentials'.
  Secret key: database/credentials
  Field path: database.port
  Available fields: username, password, host
  Provider: gcp (where the secret was found)
```

**Note**: JSON field errors do NOT trigger fallback - the secret was found, just the field extraction failed.

### Invalid JSON

When the secret value is not valid JSON but field extraction is requested:

```
SecretParsingException: Secret 'database/credentials' is not valid JSON.
  Secret key: database/credentials
  Field path: password
  Content preview: "plain-text-value..."
  Provider: aws
  Suggestion: Remove 'field' attribute if secret is plain text
```

---

## Validation Rules

| Rule | Validated At | Error |
|------|--------------|-------|
| `value` not blank | Startup | `SecretConfigurationException` |
| `provider` valid enum (if set) | Startup | `SecretConfigurationException` |
| `providers` all valid enums (if set) | Startup | `SecretConfigurationException` |
| `field` valid JSON path | Runtime | `SecretParsingException` |
| Field type is String/char[] | Startup | `SecretConfigurationException` |
| At least one provider enabled | Startup | `SecretConfigurationException` |

---

## Logging Behavior

Per FR-015, secret access is logged without revealing values:

```
INFO  SecretValueBeanPostProcessor : Injecting secret 'database/password' into field MyService.dbPassword
DEBUG SecretResolver : Resolving 'database/password' with chain: [aws, gcp, local]
DEBUG SecretResolver : Trying provider 'aws' for 'database/password'
DEBUG AwsSecretProvider : Secret 'database/password' not found in AWS Secrets Manager
DEBUG SecretResolver : Trying provider 'gcp' for 'database/password'
INFO  GcpSecretProvider : Retrieved secret 'database/password' (version: latest) [1.2KB]
DEBUG SecretCache : Cached 'database/password:latest:' (TTL: 5m)
```

**Never logged**:
- Actual secret values
- Default values
- JSON field contents

---

## Thread Safety

- `@SecretValue` injection is thread-safe (occurs during startup)
- Injected values are immutable after initialization
- Provider chain resolution is thread-safe
- Cache access is thread-safe (Caffeine is concurrent)
- Runtime refresh updates cache atomically

---

## Limitations

1. **Field injection only**: Method parameter injection not supported
2. **String types only**: No automatic type conversion
3. **No expression language**: Unlike `@Value`, does not support SpEL
4. **Static fields**: Not supported (fields must be instance fields)
5. **Final fields**: Not supported (fields must be mutable for injection)
6. **No per-provider versioning**: Version applies to all providers in chain

---

## Best Practices

### DO:
```java
// Use global provider-order for most secrets
@SecretValue("database/password")
private String dbPassword;

// Use specific provider only when secret is cloud-specific
@SecretValue(value = "aws-kms-key-id", provider = "aws")
private String kmsKeyId;

// Use custom chain for dev/prod flexibility
@SecretValue(value = "api-key", providers = {"local", "aws"})
private String apiKey;
```

### DON'T:
```java
// Don't hardcode provider when not necessary
@SecretValue(value = "generic-secret", provider = "aws")  // ❌ Limits flexibility

// Don't use both provider and providers
@SecretValue(value = "secret", provider = "aws", providers = {"gcp"})  // ❌ Confusing
```
