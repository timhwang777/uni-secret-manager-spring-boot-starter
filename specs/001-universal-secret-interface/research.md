# Research: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface

## Executive Summary

Research completed for building a Spring Boot 3.x starter library for universal secret management. All technical decisions resolved with clear implementation paths.

---

## 1. Spring Boot Auto-Configuration Pattern

### Decision
Use `@AutoConfiguration` with conditional annotations and register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Rationale
- Spring Boot 3.x introduced `@AutoConfiguration` to replace `@Configuration` + `spring.factories`
- Conditional annotations (`@ConditionalOnProperty`, `@ConditionalOnClass`) enable provider-specific beans
- `@EnableConfigurationProperties` binds YAML/properties to typed configuration classes

### Implementation Details
```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecretManagerProperties.class)
public class SecretManagerAutoConfiguration {
    // Provider beans with @ConditionalOnProperty
}
```

### Alternatives Considered
- **Manual bean registration**: Rejected - more boilerplate, less Spring-idiomatic
- **Spring Boot 2.x `spring.factories`**: Rejected - deprecated in Spring Boot 3.x

---

## 2. AWS SDK v2 Secrets Manager Integration

### Decision
Use synchronous `SecretsManagerClient` with default credential chain. Create as singleton bean with proper shutdown handling.

### Rationale
- AWS SDK v2 is the current generation with better performance and modularity
- Default credential chain covers all deployment scenarios (local, EC2, ECS, Lambda)
- Synchronous client sufficient for startup-time secret injection (no reactive requirement)

### Implementation Details
```java
SecretsManagerClient.builder()
    .region(Region.of(configuredRegion))  // Optional override
    .build();  // Uses DefaultCredentialsProvider
```

**Error Handling**:
- `ResourceNotFoundException` → `SecretNotFoundException`
- `DecryptionFailureException` → `SecretProviderException`
- `AccessDeniedException` → `SecretProviderException`

### Alternatives Considered
- **AWS Secrets Manager Caching Library**: Rejected - adds complexity; Caffeine cache is simpler and covers requirements
- **Async client**: Rejected - unnecessary for startup-time injection; adds complexity

---

## 3. GCP Secret Manager Integration

### Decision
Use `SecretManagerServiceClient` with Application Default Credentials (ADC). Reference specific versions (not "latest") in production.

### Rationale
- ADC automatically handles credentials across environments (local, GKE, Compute Engine)
- Specific versions ensure consistency; "latest" can cause issues during rotation
- Client auto-closes resources when used properly

### Implementation Details
```java
SecretVersionName.of(projectId, secretId, versionId);
client.accessSecretVersion(secretVersionName).getPayload().getData().toStringUtf8();
```

**Version Handling**:
- Default to "latest" for development convenience
- Recommend specific version numbers for production (configurable)

### Alternatives Considered
- **Spring Cloud GCP Secret Manager Starter**: Rejected - brings Spring Cloud dependency chain; we want minimal footprint

---

## 4. Caching Strategy with Caffeine

### Decision
Use Caffeine with `expireAfterWrite` policy and 5-minute default TTL. Direct Caffeine cache (not Spring Cache abstraction) for simplicity.

### Rationale
- `expireAfterWrite` ensures secrets refresh regardless of read frequency (security best practice)
- 5-minute TTL balances cost savings with secret rotation responsiveness
- Direct Caffeine API is simpler than `@Cacheable` for this use case

### Implementation Details
```java
Cache<String, String> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(5))
    .maximumSize(1000)
    .build();
```

**Refresh Mechanism**:
- Manual invalidation via `cache.invalidateAll()` for refresh endpoint
- Scheduled refresh optional (P3 feature)

### Alternatives Considered
- **Spring Cache abstraction**: Rejected - adds annotation complexity; direct API cleaner for library
- **No caching**: Rejected - violates SC-007 (95% API call reduction)

---

## 5. BeanPostProcessor for Annotation Injection

### Decision
Implement `BeanPostProcessor` using `ReflectionUtils.doWithFields()` in `postProcessBeforeInitialization`.

### Rationale
- Standard Spring pattern for custom annotation processing (similar to `@Value`)
- `postProcessBeforeInitialization` ensures values set before `@PostConstruct` methods run
- `ReflectionUtils` provides safe field access across different visibility levels

### Implementation Details
```java
@Override
public Object postProcessBeforeInitialization(Object bean, String beanName) {
    ReflectionUtils.doWithFields(bean.getClass(), field -> {
        SecretValue annotation = field.getAnnotation(SecretValue.class);
        if (annotation != null) {
            String value = secretCache.get(annotation.value(),
                key -> secretProvider.getSecret(key));
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, value);
        }
    }, field -> field.isAnnotationPresent(SecretValue.class));
    return bean;
}
```

### Alternatives Considered
- **Custom `@Value` resolver**: Rejected - requires more complex integration with Spring's property resolution
- **Method injection**: Rejected - field injection is simpler for secrets (no setter boilerplate)

---

## 6. Retry Strategy

### Decision
Implement exponential backoff with 3 retries (1s, 2s, 4s) using simple loop with `Thread.sleep()`.

### Rationale
- Matches clarification decision from spec
- No need for external retry library (Resilience4j) for simple startup retry
- KISS principle: simple loop is sufficient

### Implementation Details
```java
public String getSecretWithRetry(String key) {
    int[] delays = {1000, 2000, 4000};
    Exception lastException = null;

    for (int i = 0; i <= delays.length; i++) {
        try {
            return provider.getSecret(key);
        } catch (SecretProviderException e) {
            lastException = e;
            if (i < delays.length) {
                Thread.sleep(delays[i]);
            }
        }
    }
    throw new SecretProviderException("Failed after retries", lastException);
}
```

### Alternatives Considered
- **Resilience4j**: Rejected - overkill for startup-only retry; adds dependency
- **Spring Retry**: Rejected - same reason; simple code suffices

---

## 7. Local Development Provider

### Decision
Implement `LocalSecretProvider` that reads from Spring Environment (properties/YAML files).

### Rationale
- Matches clarification decision (properties/YAML source)
- Leverages existing Spring configuration mechanism
- No additional file parsing required

### Implementation Details
```java
@ConditionalOnProperty(prefix = "secrets", name = "provider", havingValue = "local")
public class LocalSecretProvider implements SecretProvider {
    private final Environment environment;

    public String getSecret(String key) {
        return environment.getProperty("secrets.local." + key);
    }
}
```

**Configuration Example**:
```yaml
secrets:
  provider: local
  local:
    database-password: "dev-password"
    api-key: "dev-api-key"
```

### Alternatives Considered
- **Separate JSON file**: Rejected - adds parsing complexity; properties/YAML native to Spring
- **Environment variables only**: Rejected - less convenient for multiple secrets

---

## Dependencies Summary

```xml
<!-- Core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>

<!-- AWS (optional) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>secretsmanager</artifactId>
    <optional>true</optional>
</dependency>

<!-- GCP (optional) -->
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-secretmanager</artifactId>
    <optional>true</optional>
</dependency>

<!-- Caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Utilities -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>localstack</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Open Items

None. All technical decisions resolved.
