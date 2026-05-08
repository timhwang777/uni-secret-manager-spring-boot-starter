# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Maven Spring Boot starter for universal secret resolution. Main code lives under `src/main/java/io/github/timhwang777/unisecret`, organized by role: `annotation`, `cache`, `config`, `exception`, `processor`, `provider`, `service`, and `util`. Runtime resources are in `src/main/resources`, including Spring Boot auto-configuration metadata under `META-INF/spring`. Tests live in `src/test/java/io/github/timhwang777/unisecret`, split into `unit` for isolated tests and `integration` for Docker/Testcontainers-backed provider tests. Design notes and release docs are in `specs/` and `docs/`.

## Build, Test, and Development Commands

Use Maven from the repo root; `./mvnw` is available when you want the pinned wrapper.

- `./mvnw compile`: compile Java sources and run validation-phase checks.
- `./mvnw test`: run the JUnit 5 test suite and generate the JaCoCo report.
- `./mvnw test -Dtest=AwsSecretProviderTest`: run one test class; use `ClassName#methodName` for one method.
- `./mvnw verify`: run full local checks, including Checkstyle, tests, JaCoCo coverage, and SpotBugs.
- `./mvnw package`: build the starter JAR.

Integration tests require Docker because AWS, GCP, and Vault scenarios use Testcontainers.

## Coding Style & Naming Conventions

Follow the project Checkstyle configuration in `checkstyle.xml`, based on Google Java Style. Use spaces, no tabs, no star imports, one top-level class per file, braces for control statements, and clear Java naming (`SecretResolver`, `AwsSecretProviderTest`, `ProviderType`). Keep files focused; Checkstyle caps Java files at 500 lines and cyclomatic complexity at 15. Lombok is configured in `lombok.config`; prefer existing project patterns before adding new boilerplate or abstractions.

## Testing Guidelines

Tests use JUnit 5, Mockito/AssertJ via `spring-boot-starter-test`, Awaitility, and Testcontainers. Name tests `*Test.java` and place fast mocked tests in `unit`; place provider or Spring context tests in `integration`. Maintain the enforced JaCoCo minimum of 80% line coverage, checked by `./mvnw verify`. View coverage with `open target/site/jacoco/index.html` after `./mvnw test`.

## Commit & Pull Request Guidelines

Recent history uses Conventional Commits such as `fix: ...`, `feat(ci): ...`, and `fix(vault): ...`; keep subjects imperative and scoped when helpful. Before opening a PR, run `./mvnw verify`, describe behavior changes, mention affected providers/configuration, and link related specs or issues. Include logs or screenshots only when they clarify CI, publishing, or integration-test behavior.

## Security & Configuration Tips

Do not commit real cloud credentials, Vault tokens, or secret values. Use test configuration and containerized services for local verification. When changing publishing, dependency, or provider behavior, update `README.md` or `docs/` alongside code.
