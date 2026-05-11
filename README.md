# Universal Secret Manager

Secret resolution library for retrieving secrets from multiple backends through one annotation-based API.
Version 2.x uses explicit Spring Boot line starters instead of one general starter artifact.

## Features

- `@SecretValue` field injection for secret values in Spring beans
- Explicit provider fallback chain (`aws`, `gcp`, `vault`, `local`, or custom ids)
- Per-field provider override (`provider`) and custom provider chains (`providers`)
- JSON field extraction (`field = "..."`) from structured secrets
- Version/stage support (`version = "latest"`, specific versions, AWS `previous`)
- In-memory caching with Caffeine (TTL + max size)
- Retry with exponential backoff for retryable provider errors
- Runtime cache invalidation via `SecretRefreshService`
- Passive auto-configuration: no providers are enabled unless configured

## Requirements

- Java 17+ bytecode
- Maven 3.6.3+
- Spring Boot 3.5.x: use `uni-secret-manager-spring-boot3-starter`
- Spring Boot 4.0.x: use `uni-secret-manager-spring-boot4-starter`

## Installation

Choose the starter that matches your Spring Boot line.

### Spring Boot 3 Applications

```xml
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot3-starter</artifactId>
  <version>2.0.0-M1</version>
</dependency>
```

### Spring Boot 4 Applications

```xml
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot4-starter</artifactId>
  <version>2.0.0-M1</version>
</dependency>
```

The 1.x `uni-secret-manager-spring-boot-starter` coordinate is not published as a 2.x artifact.

## Quick Start (Local Provider)

`application.yml`:

```yaml
secrets:
  provider-order:
    - local
  local:
    enabled: true
    secrets:
      database-password: dev-password-123
      api-key: dev-api-key-456
```

Bean usage:

```java
import io.github.timhwang777.unisecret.annotation.SecretValue;
import org.springframework.stereotype.Service;

@Service
public class MyService {

    @SecretValue("database-password")
    private String dbPassword;

    @SecretValue("api-key")
    private String apiKey;
}
```

No extra `@Enable...` setup is required; auto-configuration is provided by the starter.

## Annotation Usage

```java
// Basic secret
@SecretValue("database-password")
private String dbPassword;

// Default value if not found
@SecretValue(value = "optional-secret", defaultValue = "fallback")
private String optional;

// Force one provider only
@SecretValue(value = "payment-key", provider = "aws")
private String awsOnly;

// Custom provider chain for this field
@SecretValue(value = "payment-key", providers = {"local", "aws"})
private String localThenAws;

// Extract field from JSON secret
@SecretValue(value = "db-config", field = "password")
private String dbPasswordFromJson;

// Version/stage
@SecretValue(value = "db-config", version = "latest")
private String latest;
```

Provider chain precedence per field:

1. `provider` (single provider)
2. `providers` (custom list)
3. global `secrets.provider-order`

## Provider Configuration

### AWS Secrets Manager

```yaml
secrets:
  provider-order: [aws]
  aws:
    enabled: true
    region: us-east-1
    # endpoint: http://localhost:4566  # Optional (LocalStack)
```

- Credentials use the AWS SDK default chain.
- Version mapping:
  - `latest` -> `AWSCURRENT`
  - `previous` -> `AWSPREVIOUS`
  - other values -> AWS `versionId`

### GCP Secret Manager

```yaml
secrets:
  provider-order: [gcp]
  gcp:
    enabled: true
    project-id: my-gcp-project
    default-version: latest
```

- Authentication uses Application Default Credentials (ADC).
- `project-id` is required when the GCP provider is enabled.

### Local Provider (Dev/Test)

```yaml
secrets:
  provider-order: [local]
  local:
    enabled: true
    secrets:
      db-password: local-pass
```

The local provider resolves only values from `secrets.local.secrets`.

## Core Configuration Options

```yaml
secrets:
  enabled: true
  provider-order: [aws, gcp, local]
  fail-on-missing: true

  cache:
    enabled: true
    ttl: 5m
    max-size: 1000

  retry:
    max-attempts: 3
    initial-delay: 1s
    multiplier: 2.0
    max-delay: 10s
```

Notes:

- `fail-on-missing=true`: throws `SecretNotFoundException` when no provider returns a secret and no `defaultValue` is set.
- `fail-on-missing=false`: returns `null` for unresolved secrets (unless `defaultValue` is set).
- Sample configuration lives in [docs/examples/application.yml](docs/examples/application.yml); it is not shipped in starter jars.
- AWS binary secrets are unsupported in 2.0 and fail with a non-retryable provider error.

## Runtime Refresh

Use `SecretRefreshService` to invalidate cache entries after secret rotation:

```java
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecretRefreshController {

    private final SecretRefreshService refreshService;

    public SecretRefreshController(SecretRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @PostMapping("/internal/secrets/refresh-all")
    public void refreshAll() {
        refreshService.refreshAll();
    }
}
```

Available operations:

- `refreshService.refresh("secret-key")`
- `refreshService.refresh("secret-key", "version")`
- `refreshService.refresh("secret-key", "version", "field")`
- `refreshService.refreshAll()`
- `refreshService.getRefreshStats()`

## Profile-Based Example

```yaml
# application-dev.yml
secrets:
  provider-order: [local]
  local:
    enabled: true
    secrets:
      database-password: dev-password

---
# application-prod.yml
secrets:
  provider-order: [aws, gcp]
  aws:
    enabled: true
    region: us-east-1
  gcp:
    enabled: true
    project-id: my-prod-project
```

## Build, Test, and Verify

```bash
# Compile
mvn compile

# Run tests
mvn test

# Full checks (tests + quality gates + coverage threshold)
mvn verify
```

CI (`.github/workflows/ci.yml`) runs `mvn verify` on pull requests.

## Development Notes

- Source code: `src/main/java/io/github/timhwang777/unisecret`
- Tests: `src/test/java/io/github/timhwang777/unisecret`
- Auto-configuration import:
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

Additional docs:

- `docs/DEVELOPMENT_GUIDE.md`
- `docs/PUBLISHING_GUIDE.md`
- `specs/001-universal-secret-interface/quickstart.md`

## Troubleshooting

- `No secret providers are configured`:
  - Ensure at least one provider bean is enabled (`secrets.aws.enabled`, `secrets.gcp.enabled`, or `secrets.local.enabled`).
- Secret not found across providers:
  - Check `secrets.provider-order`, key name, credentials, and project/region settings.
  - Add `defaultValue` for optional secrets.
- AWS permission errors:
  - Verify IAM permission for `secretsmanager:GetSecretValue`.
- GCP permission errors:
  - Verify `secretmanager.versions.access` permissions for the principal used by ADC.

## License

MIT
