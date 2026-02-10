# Universal Secret Manager Spring Boot Starter

Spring Boot starter for retrieving secrets from multiple backends (AWS Secrets Manager, GCP Secret Manager, and local config) through one annotation-based API.

## Features

- `@SecretValue` field injection for secret values in Spring beans
- Provider fallback chain (`aws -> gcp -> local` by default)
- Per-field provider override (`provider`) and custom provider chains (`providers`)
- JSON field extraction (`field = "..."`) from structured secrets
- Version/stage support (`version = "latest"`, specific versions, AWS `previous`)
- In-memory caching with Caffeine (TTL + max size)
- Retry with exponential backoff for retryable provider errors
- Runtime cache invalidation via `SecretRefreshService`
- Profile-friendly configuration (`application-dev.yml`, `application-prod.yml`, etc.)

## Requirements

- Java 21+
- Maven 3.8+
- Spring Boot 3.2+

## Installation

This project is configured to publish artifacts to GitHub Packages.

### 1. Configure GitHub credentials (`~/.m2/settings.xml`)

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
    </server>
  </servers>
</settings>
```

PAT scope needed for consumption: `read:packages`.

### 2. Add GitHub Packages repository in your app `pom.xml`

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/timhwang777/uni-secret-manager-spring-boot-starter</url>
  </repository>
</repositories>
```

### 3. Add dependency

```xml
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Use the latest published tag version from this repository.

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
- If `project-id` is omitted, default ADC project resolution is used.

### Local Provider (Dev/Test)

```yaml
secrets:
  provider-order: [local]
  local:
    enabled: true
    secrets:
      db-password: local-pass
```

Implementation lookup order in `LocalSecretProvider#getSecret(String key, String version)`:

1. `secrets.local.secrets.<key>`
2. `secrets.local.<key>`
3. `<key>`

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
