# Release Notes: 2.0.0-M1

This milestone introduces the Spring Boot 3 and Spring Boot 4 refactor.

## Artifact Changes

- Added `io.github.timhwang777:uni-secret-manager-core`.
- Added `io.github.timhwang777:uni-secret-manager-spring-boot3-autoconfigure`.
- Added `io.github.timhwang777:uni-secret-manager-spring-boot3-starter`.
- Added `io.github.timhwang777:uni-secret-manager-spring-boot4-autoconfigure`.
- Added `io.github.timhwang777:uni-secret-manager-spring-boot4-starter`.
- Removed the 2.x general `uni-secret-manager-spring-boot-starter` artifact.

## Migration

Spring Boot 3 applications should depend on:

```xml
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot3-starter</artifactId>
  <version>2.0.0-M1</version>
</dependency>
```

Spring Boot 4 applications should depend on:

```xml
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot4-starter</artifactId>
  <version>2.0.0-M1</version>
</dependency>
```

## Behavior Changes

- Library bytecode targets Java 17.
- Auto-configuration is passive by default; providers must be enabled explicitly.
- Default provider order is empty; configure `secrets.provider-order` explicitly.
- `secrets.enabled=false` disables secret infrastructure, including Vault.
- User-defined infrastructure beans back off auto-configured defaults.
- Local provider reads only `secrets.local.secrets`.
- Vault provider logic uses Spring-free UniSecret options; Spring Vault calls stay behind adapter internals.
- GCP requires `secrets.gcp.project-id` when enabled.
- AWS binary secrets are unsupported in 2.0 and fail as non-retryable provider errors.
- Default fallback values are returned but not cached.
- Cache keys include provider selection to prevent cross-provider collisions.

## Verification

- Core contract tests pass.
- Boot 3 auto-configuration tests pass against the locally available Boot 3.5.x line.
- Boot 4 verification requires Spring Vault 4.x and Boot 4 test dependencies to be resolvable.
