# Refactor Plan: Spring Boot 3 and 4 Support

**Date**: 2026-05-08  
**Last Updated**: 2026-05-11  
**Status**: Planning - design decisions resolved  
**Scope**: Refactor the starter so it can support Spring Boot 3 and Spring Boot 4 without locking the codebase into the current package structure.

## Executive Summary

The correct long-term shape is a shared, Spring Boot-free core plus Spring Boot line-specific adapter artifacts.

I would not try to make the current single Maven artifact directly support every Boot 3 and Boot 4 combination by accident. Spring Boot 4 uses Spring Framework 7, while Spring Boot 3.5 uses Spring Framework 6.2. The auto-configuration APIs are intentionally similar, but dependency lines around Spring integrations, especially Spring Vault, are not the same. A single jar can work only if every transitive dependency is compatible across both lines. That is a weak support contract for a security-focused library.

Target design:

```text
uni-secret-manager-parent
├── uni-secret-manager-core
├── uni-secret-manager-spring-boot-autoconfigure-common  # shared source area, not a runtime dependency
├── uni-secret-manager-spring-boot3-autoconfigure
├── uni-secret-manager-spring-boot3-starter
├── uni-secret-manager-spring-boot4-autoconfigure
└── uni-secret-manager-spring-boot4-starter
```

This gives us one implementation of the domain logic and explicit Boot-line starter artifacts. Common Boot adapter source should be shared where it compiles cleanly against both lines, but each published autoconfigure artifact must be compiled and tested against its own Boot/Spring dependency set. There is no general `uni-secret-manager-spring-boot-starter` artifact in 2.0.

## Current Facts

- Current project baseline: Java 21, Maven, Spring Boot 3.2.1.
- Current artifact: `uni-secret-manager-spring-boot-starter`.
- Current code ships runtime sample configuration in `src/main/resources/application.yml`, which is wrong for a starter.
- Current auto-configuration creates provider clients directly and does not consistently back off from user-defined beans.
- Current code uses `spring-vault-core` explicitly, and Spring Vault has separate stable lines for Boot 3-era and Boot 4-era stacks.

Current official versions checked on 2026-05-11:

- Spring Boot 3.5.14 requires Java 17+ and Spring Framework 6.2.18+.
- Spring Boot 4.0.6 requires Java 17+ and Spring Framework 7.0.7+.
- Spring Vault current stable is 4.0.2, with 3.2.0 also listed as a stable line.

Reference docs:

- Spring Boot 4 system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot 3.5 system requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Spring Boot auto-configuration guide: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- Spring Boot 4 migration guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- Spring Vault reference: https://docs.spring.io/spring-vault/reference/index.html

## Support Policy

Initial supported matrix:

| Line | Target | Notes |
|------|--------|-------|
| Spring Boot 3 | 3.5.x | Use the latest 3.5 patch as the supported Boot 3 baseline. |
| Spring Boot 4 | 4.0.x | Use the latest 4.0 patch as the supported Boot 4 baseline. |
| Java | 17 bytecode | Boot 3 and 4 both support Java 17+. Java 21 can remain a CI runtime, but library bytecode should target 17 for wider compatibility. |
| Maven | 3.6.3+ | Matches Spring Boot documented minimum. |

Compatibility policy:

- Do not claim all Boot 3 minors on day one. Boot 3.2 can remain a legacy smoke-test target if needed, but the official support line should be Boot 3.5.x first.
- Do not depend on Spring Boot internals. Use only public auto-configuration APIs.
- Do not shade Spring Boot, Spring Framework, Spring Vault, Jackson, or cloud SDKs.
- Do not publish one artifact that silently pulls Boot 3 dependencies into a Boot 4 app.
- Do not keep the existing `uni-secret-manager-spring-boot-starter` artifact ID in 2.0. Users must choose the Boot 3 or Boot 4 starter explicitly.

## Architecture Decision

### Decision: Shared Core, Boot-Specific Adapters

The shared core owns:

- `@SecretValue`
- `SecretProvider`
- `ProviderId`, using an open provider id value rather than a closed enum
- `SecretReference`
- `SecretResolver`
- cache key generation
- retry policy
- JSON field extraction
- exceptions
- AWS and GCP provider implementations
- local provider implementation backed by explicit option data, not Spring `Environment`

Core must not require a code change every time a future provider is added. Built-in providers can expose constants for `aws`, `gcp`, `vault`, and `local`, but resolver behavior must depend on normalized `ProviderId` values, not on a closed `ProviderType` enum.

Keeping AWS and GCP providers in core for 2.0-M1 is an intentional batteries-included tradeoff, not a claim that core is dependency-light. Provider-specific modules can be reconsidered after the Boot 3/Boot 4 split is proven.

The shared Boot adapter source area owns code that compiles cleanly against both Boot lines:

- `@ConfigurationProperties`
- shared auto-configuration structure and bean factories
- Spring `BeanPostProcessor` implementation for `@SecretValue`
- mapping Boot properties into core option records
- local provider mapping from Spring configuration/environment into explicit core options
- mapping Vault Spring properties into Spring-free Vault option data

The Boot-line autoconfigure modules own:

- Spring bean registration
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Boot-line dependency management
- Spring Vault dependency selection and wiring for each Boot/Spring Vault line

Why this is the right split:

- Core behavior can be tested without Spring context startup.
- Boot 3 and Boot 4 can import different BOMs and dependency versions.
- The public API remains stable while integration code can evolve per Boot line.
- Common adapter code is maintained once while still being compiled separately against Boot 3 and Boot 4.
- We avoid deep Java packages and avoid provider-framework coupling.
- Future providers can be added through the `SecretProvider` SPI without editing core provider enums.

### Decision: Open Provider Identity, Not a Closed Provider Enum

Do not make `ProviderType` the long-term provider registry. A closed enum is convenient for the current built-in set, but it blocks third-party and future first-party providers because every new provider would require a core code change.

Target shape:

```java
public interface SecretProvider {
    ProviderId getProviderId();
    Optional<String> getSecret(String key, String version);
    void validateConfiguration();
    boolean isEnabled();
}
```

Use a small value object for normalization and validation:

```java
public record ProviderId(String value) {
}
```

Rules:

- Normalize provider ids at the boundary: trim and lowercase using `Locale.ROOT`.
- Allow only provider ids matching `[a-z0-9][a-z0-9-_.]*[a-z0-9]`.
- Reject blank ids, ids with whitespace, path-like values, and ids longer than 64 characters.
- Built-in ids should be constants, not enum-only behavior.
- Resolver maps providers by normalized `ProviderId`.
- Duplicate provider ids after normalization should fail fast with `SecretConfigurationException`.
- Invalid provider id syntax should fail fast at binding/reference construction.
- Referenced but unregistered provider ids should fail with clear configuration/resolution errors when annotation validation or resolution needs that provider.
- A custom `SecretProvider` with id `azure`, `doppler`, `onepassword`, or any other valid id must work without changing core code.
- Cache keys must include the effective provider id or provider chain snapshot exactly as resolved.
- The default global provider order in 2.0 is empty; users must configure provider order when they enable providers.

`ProviderType` can be kept temporarily as a migration helper if needed, but it should not remain the primary public SPI in 2.0.

### Decision: Keep Spring Vault, Hide It Behind UniSecret Boundaries

Do not reimplement Vault HTTP, authentication, TLS, token lifecycle, namespace, or KV handling during this refactor. Spring Vault already owns those integration concerns and has separate stable lines for the Spring Boot 3 and Spring Boot 4 stacks.

The right boundary is:

- UniSecret public API exposes only `SecretProvider`, provider ids, options, and resolver types.
- Spring Vault types such as `VaultTemplate`, `VaultEndpoint`, `SessionManager`, and `ClientAuthentication` remain adapter implementation details.
- `VaultSecretProvider` must not depend directly on `SecretManagerProperties.Vault`; use a Spring-free `VaultSecretProviderOptions` record.
- Add a package-private `VaultSecretOperations` abstraction and a `SpringVaultSecretOperations` implementation that owns `VaultTemplate`.

Example target shape:

```java
interface VaultSecretOperations {
    Optional<Map<String, Object>> read(String key, String version);
}
```

Then `VaultSecretProvider` owns UniSecret behavior, while `SpringVaultSecretOperations` owns Spring Vault calls.

### Decision: Shared Adapter Source, Separate Published Artifacts

Do not copy-paste the Boot 3 and Boot 4 adapters. Use a shared source area, for example:

```text
uni-secret-manager-spring-boot-autoconfigure-common/src/main/java
```

The Boot 3 and Boot 4 autoconfigure modules should compile those sources against their own BOMs, using Maven source-root configuration or another explicit build mechanism. Do not publish a common Spring-compiled runtime jar that both adapters depend on; a common jar would have to be compiled against one Spring line and could recreate the cross-line dependency risk.

### Rejected Option: Single Artifact Only

Single artifact support is attractive, but it creates several risks:

- The published POM would need to pick one Spring Boot and Spring Vault version.
- Boot 4 users could accidentally receive Spring Framework 6-era dependencies through transitive resolution.
- Vault support is especially exposed because Spring Vault 3.x and 4.x track different Spring Framework generations.
- CI would prove only selected cases, not a clean support boundary.

2.0 should not publish a general Spring Boot starter. The migration path is explicit: Boot 3 users choose `uni-secret-manager-spring-boot3-starter`; Boot 4 users choose `uni-secret-manager-spring-boot4-starter`.

### Rejected Option: Provider Module Explosion

Do not split into separate AWS, GCP, Vault, local, cache, annotation, and resolver artifacts during this refactor. That would make the module graph deep before we need it.

Acceptable published module count for this refactor: five modules. The shared Boot adapter source area is an internal build/source organization detail, not a user-facing runtime artifact. Provider-specific artifacts can be reconsidered only if dependency size or optional provider loading becomes a real user problem.

## Target Maven Layout

```text
pom.xml                                      # parent, packaging=pom
uni-secret-manager-core/pom.xml              # no Spring Boot dependency
uni-secret-manager-spring-boot-autoconfigure-common/
                                             # shared source area; no published runtime dependency
uni-secret-manager-spring-boot3-autoconfigure/pom.xml
uni-secret-manager-spring-boot3-starter/pom.xml
uni-secret-manager-spring-boot4-autoconfigure/pom.xml
uni-secret-manager-spring-boot4-starter/pom.xml
```

Parent responsibilities:

- Java release target: 17.
- Shared plugin management: compiler, surefire, failsafe, jacoco, checkstyle, spotbugs, javadoc, source jar.
- No Spring Boot parent inheritance at the root.
- Import Spring Boot BOMs only inside Boot-specific modules.

Shared adapter source area responsibilities:

- Keep common Boot adapter code in one place.
- Compile the same source through the Boot 3 and Boot 4 autoconfigure modules.
- Never publish it as a common Spring runtime jar.
- Keep Spring Vault code out of core.
- Keep Spring Vault-backed code in shared adapter source only when it compiles cleanly against both Spring Vault lines; otherwise isolate it in Boot-line modules.
- Map `SecretManagerProperties.Vault` into Spring-free `VaultSecretProviderOptions`.

Boot 3 module dependency strategy:

- Import `org.springframework.boot:spring-boot-dependencies:3.5.x`.
- Use Spring Boot 3.5 auto-configuration APIs.
- Use Spring Vault 3.2.x for Vault support.
- Keep Spring Vault types out of UniSecret public API.
- Test against Java 17 and Java 21 at minimum.

Boot 4 module dependency strategy:

- Import `org.springframework.boot:spring-boot-dependencies:4.0.x`.
- Use Spring Boot 4 auto-configuration APIs.
- Use Spring Vault 4.0.x for Vault support.
- Keep Spring Vault types out of UniSecret public API.
- Test against Java 17 and Java 21 at minimum.

Starter modules:

- Contain no source code unless absolutely necessary.
- Depend on the matching `autoconfigure` module.
- Pull provider runtime dependencies for that Boot line.
- Keep Maven coordinates obvious for users:
  - `io.github.timhwang777:uni-secret-manager-spring-boot3-starter`
  - `io.github.timhwang777:uni-secret-manager-spring-boot4-starter`

Removed legacy artifact:

- Do not publish `io.github.timhwang777:uni-secret-manager-spring-boot-starter` in 2.0.
- Document that 1.x users must migrate to the explicit Boot 3 or Boot 4 starter coordinate.
- Do not add a Boot 3 default alias; explicit coordinates are part of the 2.0 architecture.

## Target Java Package Layout

Keep packages shallow:

```text
io.github.timhwang777.unisecret.annotation
io.github.timhwang777.unisecret.cache
io.github.timhwang777.unisecret.config
io.github.timhwang777.unisecret.exception
io.github.timhwang777.unisecret.provider
io.github.timhwang777.unisecret.processor
io.github.timhwang777.unisecret.service
io.github.timhwang777.unisecret.util
io.github.timhwang777.unisecret.autoconfigure
```

Rules:

- Do not introduce packages like `provider.aws.internal.client.config`.
- If a package needs a second nested level, challenge the design first.
- Use package-private helpers inside the same package before adding new package layers.
- Keep public API types small and documented.

## Refactor Phases

### Phase 0: Baseline and Contract Tests

Goal: Freeze current behavior before moving code.

Add tests first:

- `SecretResolverContractTest`
  - resolves by provider chain order
  - skips disabled providers
  - uses default values
  - handles provider errors according to retryability
  - does not cache provider-specific results under the wrong key
  - does not cache default values
  - allows construction with zero providers and fails only on resolution when needed
  - exposes `find(...)` methods for absence-aware resolution
- `SecretReferenceContractTest`
  - validates blank keys
  - preserves caller-provided provider names
  - preserves explicit provider chain behavior
- `ProviderChainResolutionContractTest`
  - normalizes provider names without mutating configuration
  - validates invalid provider id syntax
  - validates referenced but unregistered provider ids when resolution or annotation processing needs them
  - distinguishes explicit provider, custom chain, and global chain selectors
  - resolves a custom provider id without editing core provider constants
  - rejects duplicate provider ids after normalization
  - verifies the default global provider order is empty
- `JsonFieldExtractorContractTest`
  - documents current dot-path behavior
  - documents failure behavior for missing/null fields
- `StarterPassiveContextTest`
  - application starts when starter is on classpath and no providers are enabled
  - `secrets.enabled=false` disables all secret infrastructure, including annotation processing and Vault
  - user-defined beans override auto-configured beans

Gate:

```bash
./mvnw test
```

Acceptance:

- Tests expose current flaws before code is moved.
- We have a clear distinction between behavior to preserve and behavior to fix.

### Phase 1: Make Core Spring Boot-Free

Goal: Extract reusable behavior without changing public semantics.

Implementation:

- Move resolver, cache, retry, JSON extraction, exceptions, annotation, and provider API into `uni-secret-manager-core`.
- Replace the closed `ProviderType` resolver contract with normalized provider ids.
- Add `ProviderId` as the single normalization and validation boundary for provider identifiers.
- Keep built-in provider id constants for `aws`, `gcp`, `vault`, and `local`, but do not require core edits for future provider ids.
- Make the default provider order empty in 2.0.
- Replace direct dependency on `SecretManagerProperties` inside core classes with small option records:
  - `SecretCacheOptions`
  - `RetryOptions`
  - `SecretResolutionOptions`
  - `AwsSecretProviderOptions`
  - `GcpSecretProviderOptions`
  - `LocalSecretProviderOptions`
- Keep `@SecretValue` in core as stable public API.
- Keep `SecretValueBeanPostProcessor` out of core.
- Keep AWS and GCP providers in core for 2.0-M1 as an explicit batteries-included tradeoff.
- Keep Vault provider implementations out of core because they depend on Spring Vault.
- Make `LocalSecretProvider` use explicit option data only. It must not depend on Spring `Environment`.
- Ensure resolver registration works with custom provider ids supplied by user-defined `SecretProvider` beans.
- Add `find(...)` APIs returning `Optional<String>` for absence-aware resolution.
- Preserve `resolve(...)` semantics for migration, including documented `null` behavior when `fail-on-missing=false`.
- Allow `SecretResolver` construction with zero providers so starters can be passive by default.
- Remove `@Service`, `@Component`, and other Spring stereotypes from core classes.
- Keep constructors explicit and simple. Use Lombok only for value objects when it reduces boilerplate without hiding sensitive fields.
- Remove generated `toString()` exposure for secrets, tokens, and AppRole secret IDs.

Gate:

```bash
./mvnw -pl uni-secret-manager-core test
```

Acceptance:

- Core module has no `org.springframework.boot` dependency.
- Core module has no Spring Framework dependency.
- Core module has no Spring context tests.
- Secret resolution tests pass without Spring.

### Phase 2: Build Shared Adapter Source and Boot 3 Auto-Configuration

Goal: Create the common Spring Boot adapter source and compile it first through the Boot 3 integration layer against Spring Boot 3.5.

Implementation:

- Create the shared adapter source area for common Boot configuration, properties, option mapping, and annotation processing.
- Create `uni-secret-manager-spring-boot3-autoconfigure`.
- Configure the Boot 3 module to compile the shared adapter sources against the Boot 3.5 dependency line.
- Import Boot 3.5 BOM in the module.
- Add `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Move property binding into Boot 3 module.
- Use `@AutoConfiguration`.
- Use `@ConditionalOnClass`, `@ConditionalOnProperty`, and `@ConditionalOnMissingBean`.
- Do not component-scan the library.
- Register beans only through auto-configuration.
- Move sample `application.yml` out of runtime resources and into docs.
- Ensure root `secrets.enabled=false` disables Vault and all other provider auto-config.
- Ensure user-defined beans win for SDK clients, providers, cache, resolver, refresh service, annotation processor, `ObjectMapper`, and Vault infrastructure.
- Map `secrets.local.secrets` into `LocalSecretProviderOptions`.
- Remove raw direct property/environment lookup from local provider behavior in 2.0. Only `secrets.local.secrets` participates unless a future migration requirement proves an opt-in flag is necessary.
- Map `secrets.vault.*` into `VaultSecretProviderOptions` instead of passing `SecretManagerProperties.Vault` into provider logic.
- Keep `VaultTemplate` and other Spring Vault types inside adapter/wiring classes.
- Add package-private `VaultSecretOperations` and `SpringVaultSecretOperations` so provider behavior can be tested without mocking `VaultTemplate` internals.

Tests first:

- `Boot3AutoConfigurationTest`
  - no provider enabled: context starts
  - local enabled: resolver exists and resolves local secret
  - AWS enabled with user `SecretsManagerClient`: backs off or uses user bean correctly
  - GCP enabled with user `SecretManagerServiceClient`: backs off or uses user bean correctly
  - Vault disabled: no Vault beans
  - Vault enabled with token: Vault beans exist
  - Vault provider receives Spring-free options
  - `secrets.enabled=false`: no secret infrastructure beans
  - user-defined `SecretResolver`: auto-config backs off
  - user-defined `SecretCache`, `SecretRefreshService`, and `SecretValueBeanPostProcessor`: auto-config backs off
  - local provider does not resolve arbitrary application/environment properties

Gate:

```bash
./mvnw -pl uni-secret-manager-spring-boot3-autoconfigure test
```

Acceptance:

- Boot 3 auto-config is passive by default.
- User beans win over library defaults.
- No Spring stereotype scanning is required.

### Phase 3: Rebuild Boot 4 Auto-Configuration

Goal: Create the Boot 4 integration layer against Spring Boot 4.0.

Implementation:

- Create `uni-secret-manager-spring-boot4-autoconfigure`.
- Configure the Boot 4 module to compile the shared adapter sources against the Boot 4.0 dependency line.
- Import Boot 4.0 BOM in the module.
- Reuse the shared adapter source area where it compiles against both Boot lines.
- Use Spring Vault 4.x in Boot 4.
- If Boot 4 APIs diverge, isolate differences in small package-private factories, not in core behavior.

Tests first:

- Mirror Boot 3 auto-config tests in Boot 4.
- Add sample app startup test using Boot 4 dependency management.
- Add a guard that Boot 4 dependency trees do not pull Boot 3 or Spring Framework 6-era dependencies.

Gate:

```bash
./mvnw -pl uni-secret-manager-spring-boot4-autoconfigure test
```

Acceptance:

- Boot 4 module passes the same behavioral contract as Boot 3.
- Any Boot 4-specific code is thin and localized.
- Common adapter source is compiled against both Boot lines.

### Phase 4: Provider Hardening

Goal: Fix provider-level correctness while the module boundaries are clean.

AWS:

- Make retryability explicit for each mapped AWS exception.
- Support string secrets only for 2.0.
- Treat AWS binary secrets as unsupported with a clear non-retryable provider error.
- Document binary secret support as a future roadmap item.
- Validate endpoint URI eagerly when configured.
- Ensure access denied/decryption failures are non-retryable.

GCP:

- Require `project-id` when GCP provider is enabled unless a tested default project resolver is implemented later.
- Remove string-message fallback detection for not-found if a proper GCP status type is available.
- Make permission denied non-retryable.
- Test secret version name construction.

Vault:

- Use Spring Vault 3.x in Boot 3 module and Spring Vault 4.x in Boot 4 module.
- Keep Spring Vault as the Vault client implementation for this refactor.
- Add `VaultSecretProviderOptions` so Vault provider logic does not depend on Spring property binding classes.
- Add package-private `VaultSecretOperations` and `SpringVaultSecretOperations` to separate UniSecret provider behavior from Spring Vault calls.
- Apply Vault namespace if configured and test it in both Boot adapter lines.
- Make scheduler/session lifecycle Spring-managed.
- Validate auth settings before constructing authentication objects.
- Preserve KV v1/v2 behavior with contract tests.
- Ensure no UniSecret public API exposes Spring Vault types.

Local:

- Use explicit `secrets.local.secrets` map by default.
- Remove raw direct property/environment lookup from 2.0 default behavior.
- Do not add `secrets.local.direct-lookup-enabled` unless real migration evidence shows existing users require it; if added later, it must be explicit opt-in and precisely documented.

Gate:

```bash
./mvnw test
```

Acceptance:

- Provider exception retryability is deterministic.
- No provider logs secret values.
- Provider behavior is covered by unit tests without cloud services.

### Phase 5: Cache and Refresh Semantics

Goal: Prevent wrong cache hits and make refresh behavior predictable.

Implementation:

- Replace string-concatenated cache keys with a `SecretCacheKey` value object.
- Include provider selector in the key:
  - secret key
  - version
  - field
  - explicit provider, custom provider chain, or global provider chain snapshot
- Preserve global-chain caching only when the request uses the same chain.
- Cache successful provider results only.
- Do not cache `defaultValue` fallbacks.
- Use `Duration` directly for Caffeine TTL instead of truncating to seconds.
- Make `SecretRefreshService` use the same cache key factory as `SecretResolver`.

Tests first:

- resolving same secret from `provider=aws` and `provider=local` must not share cache entries
- resolving the same secret through different custom chains must not share cache entries
- default values are returned but not cached
- refresh invalidates the same key shape that resolve writes
- millisecond TTL works if configured

Gate:

```bash
./mvnw -pl uni-secret-manager-core test
```

Acceptance:

- Cache keys cannot collide across provider selection modes.
- Refresh and resolve share one cache key implementation.

### Phase 6: Build and CI Matrix

Goal: Make compatibility provable.

CI jobs:

```text
unit-core:
  JDK: 17, 21
  Command: ./mvnw -pl uni-secret-manager-core test

boot3:
  JDK: 17, 21
  Command: ./mvnw -pl uni-secret-manager-spring-boot3-autoconfigure,uni-secret-manager-spring-boot3-starter verify

boot4:
  JDK: 17, 21
  Command: ./mvnw -pl uni-secret-manager-spring-boot4-autoconfigure,uni-secret-manager-spring-boot4-starter verify

integration:
  JDK: 21
  Requires: Docker
  Command: ./mvnw -Pintegration verify

compat-samples:
  JDK: 17, 21
  Command: ./mvnw -Pcompat verify
```

Compatibility sample projects:

```text
compat-tests/
├── boot3-local-app
├── boot3-vault-app
├── boot4-local-app
└── boot4-vault-app
```

Each sample must:

- import the matching Boot BOM
- depend on the matching starter
- start a Spring context
- resolve a local secret
- verify auto-configuration condition report has expected beans
- prove no unexpected Boot 3 dependency appears in Boot 4 sample and vice versa

Add dependency checks:

- Maven Enforcer for Java and dependency convergence.
- `mvn dependency:tree` checks in CI for Boot 3 and Boot 4 sample apps.
- Optional `japicmp` or Revapi for public API checks after the first refactor release.

### Phase 7: Documentation and Migration

Goal: Make the support model clear to users.

Docs to update:

- `README.md`
- `docs/DEVELOPMENT_GUIDE.md`
- `docs/PUBLISHING_GUIDE.md`
- release notes

Required documentation:

- Which starter to use:
  - Boot 3: `uni-secret-manager-spring-boot3-starter`
  - Boot 4: `uni-secret-manager-spring-boot4-starter`
- Removal of the 1.x general starter coordinate in 2.0.
- Java baseline.
- Provider dependency behavior.
- Sample configuration moved out of runtime resources.
- Upgrade path from current artifact.
- Default provider order is empty in 2.0; users configure `secrets.provider-order` explicitly.
- AWS binary secrets unsupported in 2.0 and tracked as future roadmap work.
- Local provider reads only `secrets.local.secrets` in 2.0 unless a future migration opt-in is explicitly documented.

Migration path:

1. Release current code as last pre-refactor snapshot if needed.
2. Release refactor milestone with both Boot starters.
3. Remove the ambiguous 1.x starter coordinate from the 2.0 module set.
4. Document exact replacement coordinates for Boot 3 and Boot 4 users.

## Testing Strategy

Test categories:

| Category | Purpose | Runs By Default |
|----------|---------|-----------------|
| Core unit tests | Resolver/cache/retry/provider behavior | Yes |
| Boot auto-config tests | Context and bean registration behavior | Yes |
| Provider unit tests | SDK exception mapping and request construction | Yes |
| Compatibility sample tests | Real Maven dependency graph per Boot line | CI profile |
| Integration tests | Docker/Testcontainers provider behavior | Separate profile |

TDD order:

1. Write failing contract tests for current behavior and known bugs.
2. Add provider SPI tests proving a custom provider id can participate without core changes.
3. Move code only after tests describe expected behavior.
4. Add Boot 3 auto-config tests before writing shared adapter source and Boot 3 wiring.
5. Add Boot 4 auto-config tests before compiling shared adapter source through Boot 4.
6. Add sample app tests before publishing starter artifacts.

## Public API Policy

Stable public API:

- `@SecretValue`
- `SecretProvider`
- `ProviderId`
- `SecretReference`
- documented exceptions
- documented refresh/cache service APIs if intentionally exposed

Internal API:

- auto-configuration classes
- property binding classes
- SDK client factory helpers
- cache key implementation details
- retry internals
- shared Boot adapter source organization

Rules:

- Do not expose Boot-specific property classes from core.
- Do not put provider SDK clients in public method signatures unless the type is specifically an extension point.
- Do not expose Spring Vault types from UniSecret public API.
- Do not require a closed enum update to add a future provider.
- Do not let Lombok-generated `toString()` expose sensitive configuration.
- Prefer explicit constructors for public API types.

## Release Strategy

Recommended versioning:

- First refactor release: `2.0.0-M1`.
- Stable release after compatibility matrix is green: `2.0.0`.

Artifact coordinates:

```xml
<!-- Spring Boot 3 applications -->
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot3-starter</artifactId>
  <version>${unisecret.version}</version>
</dependency>

<!-- Spring Boot 4 applications -->
<dependency>
  <groupId>io.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot4-starter</artifactId>
  <version>${unisecret.version}</version>
</dependency>
```

Removed artifact:

- `io.github.timhwang777:uni-secret-manager-spring-boot-starter` is a 1.x coordinate only.
- 2.0 documentation must not present a default Boot 3 alias.
- Migration docs must tell users to pick the Boot 3 or Boot 4 starter explicitly.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Boot 3 and Boot 4 auto-config APIs diverge later | Medium | Keep shared adapter source constrained to APIs that compile against both lines; isolate divergence in Boot-line modules. |
| Spring Vault 3 and 4 APIs diverge | High | Keep Vault integration inside Boot-specific modules. |
| Maven dependency tree pulls wrong Spring line | High | Use Boot-specific sample apps and dependency-tree CI checks. |
| Closed provider enum blocks future providers | Medium | Use normalized provider ids in the resolver and keep built-in ids as constants only. |
| Replacing Spring Vault creates long-term maintenance burden | High | Keep Spring Vault as the adapter implementation and hide it behind UniSecret provider boundaries. |
| Module split slows development | Medium | Limit split to core plus two adapter/starter pairs. |
| Shared adapter source becomes a hidden compatibility trap | Medium | Compile the same source under both Boot 3 and Boot 4 modules in CI; never publish it as a common Spring jar. |
| Java 17 bytecode downgrade reveals Java 21-only code | Low | Run compiler with `--release 17` early. |
| Existing users depend on old artifact ID | Medium | Treat 2.0 as breaking; document exact Boot 3 and Boot 4 replacement coordinates. |
| Testcontainers slows CI | Low | Keep integration tests in explicit Docker profile. |

## Done Criteria

This refactor is complete when:

- Core has no Spring Boot dependency.
- Core has no Spring Framework dependency.
- Resolver supports custom provider ids without modifying core code.
- Boot 3 starter passes auto-config and sample-app tests against Boot 3.5.x.
- Boot 4 starter passes auto-config and sample-app tests against Boot 4.0.x.
- No 2.0 legacy/general starter artifact is published.
- Shared Boot adapter source compiles under both Boot 3 and Boot 4 modules.
- No runtime sample `application.yml` is shipped in the starter jar.
- User-defined beans override auto-configured beans.
- `secrets.enabled=false` disables all secret infrastructure, including Vault.
- Default provider order is empty.
- UniSecret public API does not expose Spring Vault types.
- Vault provider logic consumes Spring-free option data instead of `SecretManagerProperties.Vault`.
- Vault provider behavior is separated from Spring Vault calls through package-private `VaultSecretOperations`.
- Cache keys include provider selection and no longer collide.
- Default values are not cached.
- GCP provider fails fast when enabled without `project-id`.
- AWS binary secrets fail clearly as unsupported and are documented as roadmap work.
- Local direct lookup is not part of 2.0 default behavior.
- Vault namespace is applied and tested.
- Provider exception retryability is tested and explicit.
- CI proves both Boot lines on at least Java 17 and Java 21.
- README shows separate install instructions for Boot 3 and Boot 4.

## Immediate Next Steps

1. Add provider SPI contract tests for `ProviderId` normalization, syntax validation, duplicate ids, unregistered ids, empty default provider order, and custom provider ids.
2. Replace resolver internals that depend on `ProviderType` with `ProviderId`.
3. Add contract tests for passive starter behavior, provider cache key isolation, and user bean backoff.
4. Convert the root Maven project to a parent `pom`.
5. Extract `uni-secret-manager-core`.
6. Add the shared Boot adapter source area.
7. Add `VaultSecretProviderOptions`, `VaultSecretOperations`, and `SpringVaultSecretOperations` while keeping Spring Vault types inside adapter implementation code.
8. Rebuild Boot 3 auto-configuration on top of the core and shared adapter source.
9. Add Boot 4 auto-configuration by compiling the shared adapter source against Boot 4 and isolating any required divergence.
10. Add compatibility sample projects before publishing any Boot 4 claim.
