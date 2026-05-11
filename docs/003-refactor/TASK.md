# Refactor Task List: Spring Boot 3 and 4 Support

**Source plan**: `docs/003-refactor/PLAN.md`  
**Status**: In progress - ProviderId/local/Vault option cleanup complete; provider hardening still pending  
**Last reconciled**: 2026-05-11  
**Target release**: `2.0.0-M1` first, then `2.0.0` after compatibility matrix is green.

## Working Rules

- Treat `PLAN.md` as the architectural source of truth.
- Keep the refactor test-first where the plan calls for contract or auto-configuration tests.
- Keep the library bytecode target at Java 17, even if local CI also runs on Java 21.
- Do not publish or document a 2.0 general `uni-secret-manager-spring-boot-starter` artifact.
- Do not create provider-specific artifacts during this refactor.
- Do not publish the shared Boot adapter source area as a runtime dependency.
- Do not ship runtime sample `application.yml` in starter jars.
- Ensure user-defined Spring beans win over auto-configured defaults.
- Ensure `secrets.enabled=false` disables all secret infrastructure, including Vault.
- Use `ProviderId` as the open provider identity API; do not keep `ProviderType` as the primary SPI.
- Default provider order is empty in 2.0.
- Keep Spring Vault, but hide Spring Vault types behind UniSecret adapter boundaries.
- Remove local direct property/environment lookup from 2.0 default behavior.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Done
- `[!]` Blocked or needs decision

## Reconciled Completed Work

These items were already present in the workspace before this task-list update.

- [x] Root project converted to parent `pom` with packaging `pom`.
- [x] `uni-secret-manager-core` module added.
- [x] `uni-secret-manager-spring-boot-autoconfigure-common/` shared source area added.
- [x] Boot 3 autoconfigure and starter modules added.
- [x] Boot 4 autoconfigure and starter modules added.
- [x] Core resolver/cache/retry/JSON/exception/provider code moved into `uni-secret-manager-core`.
- [x] AWS, GCP, and local providers moved into core.
- [x] `SecretValueBeanPostProcessor` kept out of core.
- [x] Vault provider kept out of core while it depends on Spring Vault.
- [x] Core option records added for cache, retry, resolution, AWS, GCP, and local.
- [x] Local provider now reads explicit option data only.
- [x] Shared Boot property binding, option mapping, annotation processor, and auto-configuration source added.
- [x] Boot 3 and Boot 4 modules compile the shared adapter source.
- [x] Boot 3 and Boot 4 `AutoConfiguration.imports` files added.
- [x] Boot 3 and Boot 4 auto-configuration tests added.
- [x] Boot-line starter POMs added and point to matching autoconfigure modules.
- [x] Runtime `application.yml` is no longer present in starter/module runtime resources.
- [x] `SecretCacheKey` value object added.
- [x] Resolver cache keys include provider selection.
- [x] Default values are returned but not cached.
- [x] AWS binary secret unsupported behavior has a contract test.
- [x] GCP enabled-without-project-id behavior has a contract test.
- [x] Targeted tests passed: `./mvnw -q -pl uni-secret-manager-core,uni-secret-manager-spring-boot3-autoconfigure,uni-secret-manager-spring-boot4-autoconfigure test`.
- [x] Core dependency tree was checked and contains no Spring Boot or Spring Framework dependency.

## New Tasks Added From Grill Decisions

- [x] Add `ProviderId` value object with normalization, validation, and constants for built-ins.
- [x] Replace `SecretProvider#getProviderType()` with `SecretProvider#getProviderId()`.
- [x] Replace resolver internals that depend on `ProviderType` with `ProviderId`.
- [x] Reject invalid provider ids: blank, whitespace, path-like values, values longer than 64 chars, and values not matching `[a-z0-9][a-z0-9-_.]*[a-z0-9]`.
- [x] Fail fast on duplicate provider ids after normalization.
- [x] Support custom provider ids without modifying core.
- [x] Change the 2.0 default provider order to empty.
- [x] Keep `@SecretValue(provider=...)` and `@SecretValue(providers=...)` as string attributes and convert to `ProviderId` at the boundary.
- [x] Add `VaultSecretProviderOptions`.
- [x] Add package-private `VaultSecretOperations`.
- [x] Add `SpringVaultSecretOperations` to isolate Spring Vault calls.
- [x] Change `VaultSecretProvider` to depend on `VaultSecretProviderOptions` and `VaultSecretOperations`, not `SecretManagerProperties.Vault` or `VaultTemplate`.
- [x] Remove `directLookupEnabled` from local provider options/properties unless real migration evidence requires an explicit opt-in later.
- [x] Update docs to state local provider reads only `secrets.local.secrets` in 2.0.

## Milestone 0: Baseline and Contract Tests

Goal: freeze current behavior and expose known defects before moving code.

### Done

- [x] Add `SecretResolverContractTest`.
- [x] Verify provider chain order resolution.
- [x] Verify disabled providers are skipped.
- [x] Verify default values are used when configured.
- [x] Verify provider errors follow retryability rules.
- [x] Verify provider-specific results do not share a wrong cache key.
- [x] Verify default values are not cached.
- [x] Verify resolver can be constructed with zero providers and fails only during resolution when needed.
- [x] Add absence-aware `find(...)` contract coverage.
- [x] Add `SecretReferenceContractTest`.
- [x] Verify blank keys are rejected.
- [x] Verify caller-provided provider names are preserved.
- [x] Verify explicit provider, custom chain, and global chain selectors are distinct.
- [x] Add `ProviderChainResolutionContractTest`.
- [x] Verify provider names are normalized without mutating configuration.
- [x] Verify invalid provider names are rejected.
- [x] Add `JsonFieldExtractorContractTest`.
- [x] Document current dot-path behavior.
- [x] Document missing and null field failure behavior.
- [x] Add passive context coverage through Boot 3 and Boot 4 auto-configuration tests.
- [x] Verify app starts when starter auto-configuration is present and no providers are enabled.
- [x] Verify `secrets.enabled=false` disables annotation processing and Vault.
- [x] Verify user-defined infrastructure beans override auto-configured beans.

### Remaining

- [x] Add `ProviderId` contract tests.
- [x] Verify invalid provider id syntax is rejected.
- [x] Verify duplicate provider ids after normalization are rejected.
- [x] Verify custom provider ids resolve without changing core constants.
- [x] Verify default global provider order is empty.
- [x] Verify referenced but unregistered provider ids fail clearly during annotation validation or resolution.

### Gate

- [x] Run targeted module tests for core, Boot 3 autoconfigure, and Boot 4 autoconfigure.
- [x] Run full `./mvnw test` after ProviderId/default-order changes.
- [x] Record current expected failures: none from the targeted module test run.

### Acceptance

- [x] Current behavior to preserve is covered.
- [x] Behavior to intentionally fix is covered by tests.

## Milestone 1: Extract Spring-Free Core

Goal: move reusable secret-resolution behavior into `uni-secret-manager-core` without changing public semantics.

### Maven and Module Layout

- [x] Convert the root project into a parent `pom` with packaging `pom`.
- [x] Add `uni-secret-manager-core/pom.xml`.
- [x] Configure Java release target `17`.
- [x] Move shared plugin management to the parent POM.
- [x] Ensure the core module does not import a Spring Boot BOM.
- [x] Ensure the core module does not depend on Spring Boot or Spring Framework.

### Core Code Moves

- [x] Move `@SecretValue` into core.
- [x] Move `SecretProvider`, `ProviderType`, and provider API types into core as the current intermediate state.
- [x] Replace `ProviderType` with `ProviderId` as the primary provider identity API.
- [x] Move `SecretReference` into core.
- [x] Move `SecretResolver` into core.
- [x] Move cache abstractions and implementations into core.
- [x] Move retry policy code into core.
- [x] Move JSON field extraction into core.
- [x] Move documented exceptions into core.
- [x] Move AWS provider implementation into core.
- [x] Move GCP provider implementation into core.
- [x] Move local provider implementation into core.
- [x] Keep `SecretValueBeanPostProcessor` out of core.
- [x] Keep Vault provider implementation out of core.
- [x] Remove Spring stereotypes such as `@Service` and `@Component` from core classes.

### Core Options

- [x] Replace direct core dependency on `SecretManagerProperties`.
- [x] Add `SecretCacheOptions`.
- [x] Add `RetryOptions`.
- [x] Add `SecretResolutionOptions`.
- [x] Add `AwsSecretProviderOptions`.
- [x] Add `GcpSecretProviderOptions`.
- [x] Add `LocalSecretProviderOptions`.
- [x] Make `LocalSecretProviderOptions` represent explicit local secrets only.
- [x] Make `LocalSecretProvider` depend on explicit option data only.
- [x] Remove Spring `Environment` usage from core local provider code.

### Resolver API and Safety

- [x] Add `find(...)` APIs returning `Optional<String>`.
- [x] Preserve `resolve(...)` migration semantics, including documented `null` behavior when `fail-on-missing=false`.
- [x] Allow `SecretResolver` construction with zero providers.
- [x] Keep public constructors explicit and simple.
- [x] Remove generated `toString()` exposure for secrets, tokens, and AppRole secret IDs.
- [x] Change default `SecretResolutionOptions.providerOrder()` to empty.
- [x] Change resolver provider map from `Map<ProviderType, SecretProvider>` to `Map<ProviderId, SecretProvider>`.

### Tests

- [x] Move applicable contract tests into the core module.
- [x] Update tests to construct core option records directly.
- [x] Verify secret resolution tests pass without Spring context startup.
- [x] Update core tests for `ProviderId` and empty provider order.

### Gate

- [x] Run `./mvnw -pl uni-secret-manager-core test`.
- [x] Verify `mvn dependency:tree` for core contains no Spring Boot dependency.
- [x] Verify `mvn dependency:tree` for core contains no Spring Framework dependency.

### Acceptance

- [x] Core has no `org.springframework.boot` dependency.
- [x] Core has no Spring Framework dependency.
- [x] Core has no Spring context tests.
- [x] Core resolver, cache, retry, JSON extraction, and provider unit tests pass.
- [x] Core resolver supports custom provider ids without core changes.

## Milestone 2: Shared Adapter Source and Boot 3 Auto-Configuration

Goal: compile common Spring Boot adapter code through a Boot 3.5 integration module.

### Module Layout

- [x] Add `uni-secret-manager-spring-boot-autoconfigure-common/` as a shared source area.
- [x] Add `uni-secret-manager-spring-boot3-autoconfigure/pom.xml`.
- [x] Configure the Boot 3 autoconfigure module to compile shared adapter sources.
- [x] Import `org.springframework.boot:spring-boot-dependencies:3.5.x` in the Boot 3 module.
- [x] Add `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- [x] Ensure the shared source area is not published as a runtime dependency.

### Shared Adapter Code

- [x] Move property binding into Boot adapter code.
- [x] Add shared `@ConfigurationProperties` classes where they compile against both Boot lines.
- [x] Add mapping from Spring properties into core option records.
- [x] Add local provider mapping from Spring configuration into `LocalSecretProviderOptions`.
- [x] Add shared annotation processing support for `@SecretValue`.
- [x] Keep Vault integration out of core.
- [x] Keep Spring Vault-backed code in shared source only while it compiles against both Spring Vault lines.
- [x] Add mapping from `secrets.vault.*` into `VaultSecretProviderOptions`.
- [x] Add package-private `VaultSecretOperations` and `SpringVaultSecretOperations`.
- [x] Remove local direct lookup property/options from the default 2.0 adapter surface unless migration evidence requires it.

### Boot 3 Auto-Configuration

- [x] Use `@AutoConfiguration`.
- [x] Use `@ConditionalOnClass` for optional provider integrations.
- [x] Use `@ConditionalOnProperty` for root and provider enablement.
- [x] Use `@ConditionalOnMissingBean` for user-overridable beans currently covered by auto-configuration.
- [x] Register beans only through auto-configuration.
- [x] Remove any library component scanning requirement.
- [x] Ensure no provider is enabled by accident.
- [x] Ensure `secrets.enabled=false` disables all secret infrastructure.
- [x] Ensure `secrets.enabled=false` disables Vault.
- [x] Move sample `application.yml` out of runtime resources and into docs/runtime-free modules.
- [x] Map `secrets.local.secrets` into `LocalSecretProviderOptions`.
- [x] Remove `secrets.local.direct-lookup-enabled` unless migration evidence requires it.
- [x] Ensure Boot 3 property defaults use an empty provider order.

### Bean Backoff

- [x] Back off for user-defined SDK client beans.
- [~] Back off for user-defined provider beans.
- [x] Back off for user-defined cache beans.
- [x] Back off for user-defined `SecretResolver`.
- [x] Back off for user-defined refresh service.
- [x] Back off for user-defined `SecretValueBeanPostProcessor`.
- [x] Back off for user-defined `ObjectMapper`.
- [~] Back off for user-defined Vault infrastructure beans.
- [x] Add explicit tests for custom `SecretProvider` bean registration with custom provider id.

### Tests

- [x] Add `Boot3AutoConfigurationTest`.
- [x] Verify no provider enabled: context starts.
- [x] Verify local enabled: resolver exists and resolves a local secret.
- [x] Verify AWS enabled with user `SecretsManagerClient`: backs off or uses user bean correctly.
- [x] Verify GCP enabled with user `SecretManagerServiceClient`: backs off or uses user bean correctly.
- [x] Verify Vault disabled: no Vault beans.
- [x] Verify Vault enabled with token: Vault beans exist.
- [x] Verify `secrets.enabled=false`: no secret infrastructure beans.
- [x] Verify user-defined `SecretResolver`: auto-config backs off.
- [x] Verify user-defined `SecretCache`: auto-config backs off.
- [x] Verify user-defined `SecretRefreshService`: auto-config backs off.
- [x] Verify user-defined `SecretValueBeanPostProcessor`: auto-config backs off.
- [x] Verify local direct lookup is not active by default.
- [x] Verify Vault provider receives Spring-free options.
- [x] Verify local provider does not resolve arbitrary application/environment properties after direct lookup removal.
- [x] Verify empty provider order default.

### Gate

- [x] Run `./mvnw -pl uni-secret-manager-spring-boot3-autoconfigure test`.

### Acceptance

- [x] Boot 3 auto-configuration is passive by default.
- [x] User beans win over covered library defaults.
- [x] No Spring stereotype scanning is required.
- [x] Boot 3 auto-configuration reflects `ProviderId`, empty provider order, and Vault option/operation boundaries.

## Milestone 3: Boot 4 Auto-Configuration

Goal: compile the shared adapter source through a Boot 4.0 integration module.

### Module Layout

- [x] Add `uni-secret-manager-spring-boot4-autoconfigure/pom.xml`.
- [x] Configure the Boot 4 autoconfigure module to compile shared adapter sources.
- [x] Import `org.springframework.boot:spring-boot-dependencies:4.0.x` in the Boot 4 module.
- [x] Use Spring Vault 4.x in the Boot 4 module.
- [~] Isolate any Boot 4 API divergence in small package-private factories.

### Tests

- [x] Mirror Boot 3 auto-configuration tests for Boot 4.
- [ ] Add a Boot 4 sample app startup test.
- [ ] Add a dependency guard that Boot 4 dependency trees do not pull Boot 3 dependencies.
- [ ] Add a dependency guard that Boot 4 dependency trees do not pull Spring Framework 6-era dependencies.
- [x] Mirror new ProviderId, empty provider order, Vault options, and local lookup tests in Boot 4.

### Gate

- [x] Run `./mvnw -pl uni-secret-manager-spring-boot4-autoconfigure test`.

### Acceptance

- [x] Boot 4 module passes the same current behavior contract as Boot 3.
- [~] Boot 4-specific code is thin and localized.
- [x] Common adapter source compiles against both Boot lines.
- [ ] Boot 4 dependency guard and sample-app checks pass.

## Milestone 4: Starter Modules

Goal: expose explicit Boot-line starter artifacts.

### Boot 3 Starter

- [x] Add `uni-secret-manager-spring-boot3-starter/pom.xml`.
- [x] Depend on `uni-secret-manager-spring-boot3-autoconfigure`.
- [x] Pull provider runtime dependencies transitively through the matching autoconfigure module.
- [x] Keep source code out of the starter unless there is a concrete need.
- [x] Verify published coordinate is `io.github.timhwang777:uni-secret-manager-spring-boot3-starter`.

### Boot 4 Starter

- [x] Add `uni-secret-manager-spring-boot4-starter/pom.xml`.
- [x] Depend on `uni-secret-manager-spring-boot4-autoconfigure`.
- [x] Pull provider runtime dependencies transitively through the matching autoconfigure module.
- [x] Keep source code out of the starter unless there is a concrete need.
- [x] Verify published coordinate is `io.github.timhwang777:uni-secret-manager-spring-boot4-starter`.

### Legacy Artifact Removal

- [x] Remove the 2.0 module for `uni-secret-manager-spring-boot-starter`.
- [~] Ensure 2.0 docs do not present a Boot 3 default alias.
- [~] Add migration documentation for explicit Boot 3 and Boot 4 replacement coordinates.

### Gate

- [ ] Run `./mvnw package`.
- [ ] Inspect built jars for unexpected runtime resources.

### Acceptance

- [ ] Both starters package cleanly.
- [x] Starter module source trees do not contain runtime sample `application.yml`.
- [x] No general 2.0 starter artifact is built by the current module list.

## Milestone 5: Provider Hardening

Goal: fix provider correctness after the module boundaries are clean.

### AWS

- [~] Make retryability explicit for each mapped AWS exception.
- [x] Support string secrets only for 2.0.
- [x] Treat AWS binary secrets as unsupported with a clear non-retryable provider error.
- [ ] Validate endpoint URI eagerly when configured.
- [x] Ensure access denied failures are non-retryable in implementation.
- [x] Ensure decryption failures are non-retryable in implementation.
- [~] Document binary secret support as a future roadmap item.
- [ ] Add broader AWS exception retryability tests.

### GCP

- [x] Require `project-id` when GCP provider is enabled.
- [x] Defer default project resolver support unless it is fully tested.
- [~] Remove string-message fallback detection for not-found when a proper status type is available.
- [x] Make permission denied non-retryable in implementation.
- [ ] Test secret version name construction.
- [ ] Add permission denied retryability test.

### Vault

- [x] Keep Vault code out of core while it depends on Spring Vault.
- [x] Use Spring Vault 3.x in Boot 3.
- [x] Use Spring Vault 4.x in Boot 4.
- [x] Add `VaultSecretProviderOptions`.
- [x] Add package-private `VaultSecretOperations`.
- [x] Add `SpringVaultSecretOperations`.
- [x] Make `VaultSecretProvider` depend on options and operations instead of `SecretManagerProperties.Vault` and direct `VaultTemplate`.
- [x] Apply Vault namespace when configured in implementation.
- [ ] Test Vault namespace handling in Boot 3.
- [ ] Test Vault namespace handling in Boot 4.
- [x] Make scheduler and session lifecycle Spring-managed.
- [x] Validate auth settings before constructing authentication objects.
- [~] Preserve KV v1 behavior in implementation.
- [~] Preserve KV v2 behavior in implementation.
- [ ] Add KV v1 contract tests.
- [ ] Add KV v2 contract tests.
- [x] Ensure no UniSecret public API exposes Spring Vault types.

### Local

- [x] Use explicit `secrets.local.secrets` map by default.
- [x] Do not perform raw direct property or environment lookup in `LocalSecretProvider`.
- [x] Remove `directLookupEnabled` from local options/properties unless migration evidence requires it.
- [x] Document local provider reads only `secrets.local.secrets` in 2.0.

### Tests

- [ ] Add provider exception retryability unit tests.
- [ ] Add provider request construction unit tests.
- [x] Verify current provider unit tests do not require cloud services.
- [~] Verify providers do not log secret values.

### Gate

- [x] Run `./mvnw test` after provider hardening.

### Acceptance

- [~] Provider exception retryability is deterministic.
- [~] No provider logs secret values.
- [~] Provider behavior is covered by unit tests without cloud services.

## Milestone 6: Cache and Refresh Semantics

Goal: prevent wrong cache hits and make refresh behavior predictable.

### Implementation

- [x] Add `SecretCacheKey` value object.
- [x] Replace resolver string-concatenated cache keys.
- [x] Include secret key in cache key.
- [x] Include version in cache key.
- [x] Include field in cache key.
- [x] Include explicit provider selector in cache key.
- [x] Include custom provider chain selector in cache key.
- [x] Include global provider chain snapshot in cache key.
- [x] Preserve global-chain caching only for the same request chain.
- [x] Cache successful provider results only.
- [x] Do not cache `defaultValue` fallbacks.
- [x] Use `Duration` directly for Caffeine TTL.
- [x] Make `SecretRefreshService` expose the same cache key factory path as `SecretResolver`.

### Tests

- [x] Verify `provider=aws` and `provider=local` do not share cache entries for the same secret.
- [ ] Verify different custom chains do not share cache entries for the same secret.
- [x] Verify default values are returned but not cached.
- [ ] Verify refresh invalidates the same key shape that resolve writes.
- [ ] Verify millisecond TTL works if configured.
- [ ] Update cache tests for `ProviderId`.

### Gate

- [x] Run `./mvnw -pl uni-secret-manager-core test`.

### Acceptance

- [x] Cache keys cannot collide across explicit provider selection modes.
- [~] Refresh and resolve share one cache key implementation.

## Milestone 7: Build and CI Matrix

Goal: prove compatibility with the supported Java and Spring Boot lines.

### Maven Enforcement

- [ ] Add Maven Enforcer Java version rule.
- [ ] Add Maven Enforcer dependency convergence rule.
- [ ] Add dependency checks for Boot 3 and Boot 4 sample apps.
- [ ] Consider adding `japicmp` or Revapi after the first refactor release.

### CI Jobs

- [~] Update existing CI workflow for the module split.
- [ ] Add `unit-core` job on JDK 17.
- [ ] Add `unit-core` job on JDK 21.
- [ ] Run `./mvnw -pl uni-secret-manager-core test` in `unit-core`.
- [ ] Add `boot3` job on JDK 17.
- [ ] Add `boot3` job on JDK 21.
- [ ] Run `./mvnw -pl uni-secret-manager-spring-boot3-autoconfigure,uni-secret-manager-spring-boot3-starter verify` in `boot3`.
- [ ] Add `boot4` job on JDK 17.
- [ ] Add `boot4` job on JDK 21.
- [ ] Run `./mvnw -pl uni-secret-manager-spring-boot4-autoconfigure,uni-secret-manager-spring-boot4-starter verify` in `boot4`.
- [ ] Add Docker-backed integration job on JDK 21.
- [ ] Run `./mvnw -Pintegration verify` in the integration job.
- [ ] Add compatibility sample job on JDK 17.
- [ ] Add compatibility sample job on JDK 21.
- [ ] Run `./mvnw -Pcompat verify` in compatibility jobs.

### Compatibility Samples

- [ ] Add `compat-tests/boot3-local-app`.
- [ ] Add `compat-tests/boot3-vault-app`.
- [ ] Add `compat-tests/boot4-local-app`.
- [ ] Add `compat-tests/boot4-vault-app`.
- [ ] Ensure each sample imports the matching Boot BOM.
- [ ] Ensure each sample depends on the matching starter.
- [ ] Ensure each sample starts a Spring context.
- [ ] Ensure each sample resolves a local secret.
- [ ] Verify expected auto-configuration condition report entries.
- [ ] Prove Boot 3 samples do not pull Boot 4 dependencies unexpectedly.
- [ ] Prove Boot 4 samples do not pull Boot 3 dependencies unexpectedly.

### Gate

- [ ] Run `./mvnw verify`.
- [ ] Run `./mvnw -Pcompat verify`.
- [ ] Run `./mvnw -Pintegration verify` on a Docker-capable machine.

### Acceptance

- [ ] CI proves Boot 3 on Java 17 and Java 21.
- [ ] CI proves Boot 4 on Java 17 and Java 21.
- [ ] Compatibility samples prove clean dependency graphs.

## Milestone 8: Documentation and Migration

Goal: make the new support model clear to users.

### Documentation Updates

- [~] Update `README.md`.
- [~] Update `docs/DEVELOPMENT_GUIDE.md`.
- [~] Update `docs/PUBLISHING_GUIDE.md`.
- [x] Add release notes for `2.0.0-M1`.
- [~] Document the Boot 3 starter coordinate.
- [~] Document the Boot 4 starter coordinate.
- [~] Document removal of the 1.x general starter coordinate in 2.0.
- [~] Document Java baseline and bytecode target.
- [~] Document provider dependency behavior.
- [x] Move sample configuration out of runtime resources and into docs/runtime-free modules.
- [~] Document upgrade path from the current artifact.
- [~] Document AWS binary secrets as unsupported in 2.0.
- [~] Document AWS binary secret support as roadmap work.
- [x] Document default provider order is empty in 2.0.
- [x] Document local provider reads only `secrets.local.secrets` in 2.0.
- [ ] Document `ProviderId` and custom provider implementation rules.
- [x] Document Spring Vault is retained but hidden behind UniSecret boundaries.

### Migration Path

- [ ] Decide whether to release the current code as the last pre-refactor snapshot.
- [~] Prepare `2.0.0-M1` release notes with both Boot starters.
- [x] Remove the ambiguous 1.x starter coordinate from the 2.0 module set.
- [~] Document exact replacement coordinates for Boot 3 users.
- [~] Document exact replacement coordinates for Boot 4 users.

### Gate

- [ ] Run `./mvnw verify`.
- [ ] Review README install instructions for Boot 3 and Boot 4 separately.
- [ ] Review built artifacts and generated POMs before publishing.

### Acceptance

- [~] README shows separate install instructions for Boot 3 and Boot 4.
- [~] Migration docs tell users to choose the correct starter explicitly.
- [~] 2.0 docs do not recommend the removed general starter artifact.

## Final Done Checklist

- [x] Core has no Spring Boot dependency.
- [x] Core has no Spring Framework dependency.
- [x] `ProviderId` is the primary provider identity API.
- [x] Resolver supports custom provider ids without modifying core code.
- [x] Default provider order is empty.
- [x] Boot 3 starter passes current auto-config tests against Boot 3.5.x.
- [ ] Boot 3 starter passes sample-app tests against Boot 3.5.x.
- [x] Boot 4 starter passes current auto-config tests against Boot 4.0.x.
- [ ] Boot 4 starter passes sample-app tests against Boot 4.0.x.
- [x] No 2.0 legacy/general starter artifact is built by the current module list.
- [x] Shared Boot adapter source compiles under both Boot 3 and Boot 4 modules.
- [x] No runtime sample `application.yml` is shipped in starter source resources.
- [x] User-defined infrastructure beans override auto-configured beans in current tests.
- [x] `secrets.enabled=false` disables all current secret infrastructure, including Vault.
- [x] UniSecret public API does not expose Spring Vault types.
- [x] Vault provider logic consumes `VaultSecretProviderOptions`.
- [x] Vault provider behavior is isolated from Spring Vault calls through `VaultSecretOperations`.
- [x] Cache keys include provider selection and no longer collide for explicit provider selection.
- [x] Default values are not cached.
- [x] GCP provider fails fast when enabled without `project-id`.
- [x] AWS binary secrets fail clearly as unsupported.
- [~] AWS binary secrets are documented as roadmap work.
- [x] Local direct lookup is removed from the 2.0 default API surface.
- [~] Vault namespace is applied in implementation.
- [~] Provider exception retryability is tested and explicit.
- [ ] CI proves both Boot lines on Java 17 and Java 21.
- [~] README shows separate install instructions for Boot 3 and Boot 4.
