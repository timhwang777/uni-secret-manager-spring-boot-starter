# Development Guide

This guide covers the tools, workflows, and best practices for developing the Universal Secret Manager Spring Boot Starter.

## Table of Contents

- [Project Stack](#project-stack)
- [Build Tool: Maven](#build-tool-maven)
- [Code Coverage: JaCoCo](#code-coverage-jacoco)
- [Testing](#testing)
- [Dependency Management](#dependency-management)
- [Development Workflows](#development-workflows)
- [Quick Reference](#quick-reference)

---

## Project Stack

| Tool | Purpose |
|------|---------|
| **Java 21** | Language (LTS version) |
| **Maven** | Build automation and dependency management |
| **Spring Boot 3.2** | Framework for auto-configuration |
| **JaCoCo** | Code coverage measurement |
| **JUnit 5** | Unit testing framework |
| **Testcontainers** | Integration testing with Docker |
| **Lombok** | Reduces boilerplate code |

---

## Build Tool: Maven

Maven is the build automation tool that manages compiling, testing, packaging, and dependencies.

### Maven Lifecycle Phases

Phases run in order - each phase includes all previous phases:

```
compile → test → package → verify → install
```

| Phase | What Happens |
|-------|--------------|
| `compile` | Compiles source code |
| `test` | Runs unit tests + generates coverage report |
| `package` | Creates the JAR file |
| `verify` | Checks coverage thresholds |
| `install` | Installs JAR to local Maven repository |

### Common Commands

```bash
# Compile only (quick syntax check)
mvn compile

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=SecretManagerServiceTest

# Run a specific test method
mvn test -Dtest=SecretManagerServiceTest#testGetSecret

# Run tests matching a pattern
mvn test -Dtest=*Integration*

# Full CI check (compile + test + coverage check)
mvn verify

# Build the JAR
mvn package

# Clean build artifacts
mvn clean

# Clean and verify (recommended before commits)
mvn clean verify
```

### Skipping (Use Sparingly)

```bash
# Skip tests (when you just need the JAR quickly)
mvn package -DskipTests

# Skip coverage check only
mvn verify -Djacoco.skip=true
```

---

## Code Coverage: JaCoCo

JaCoCo (Java Code Coverage) measures how much of your code is executed by tests.

### Coverage Threshold

The project enforces **80% line coverage**. Builds fail if coverage drops below this threshold during `mvn verify`.

### Viewing Coverage Reports

After running tests, view the HTML report:

```bash
mvn test
open target/site/jacoco/index.html    # macOS
xdg-open target/site/jacoco/index.html # Linux
```

### Reading the Report

| Column | Meaning |
|--------|---------|
| **Missed Instructions** | Red = untested, Green = tested |
| **Cov. (Instructions)** | % of bytecode instructions executed |
| **Missed Branches** | Untested if/else paths |
| **Cov. (Branches)** | % of decision branches taken |

**Color coding in source view:**
- **Green** = line executed by tests
- **Yellow** = partially covered (some branches missed)
- **Red** = never executed

### Lombok and Coverage

Lombok-generated code (getters, setters, equals, hashCode) is excluded from coverage via `lombok.config`:

```properties
lombok.addLombokGeneratedAnnotation = true
```

This ensures only hand-written code is measured.

---

## Testing

### Test Structure

```
src/test/java/
├── unit/           # Fast, isolated unit tests (mocked dependencies)
└── integration/    # Slower tests with real services (Docker)
```

### Unit Tests

Unit tests mock external dependencies and run fast:

```bash
mvn test -Dtest=AwsSecretProviderTest
```

### Integration Tests with Testcontainers

Integration tests use Testcontainers to spin up real services in Docker:

```bash
# Requires Docker to be running
mvn test -Dtest=AwsIntegrationTest
```

**What happens:**
1. Testcontainers starts a LocalStack container (fake AWS)
2. Your test communicates with LocalStack
3. Container shuts down after tests complete

**Prerequisites:**
- Docker must be running (`docker ps` to verify)
- Sufficient memory for containers

**Common issues:**
- Docker not running → Start Docker Desktop
- Port conflicts → Stop conflicting services
- Timeout → Increase timeout or check Docker resources

### Test Dependencies

| Library | Purpose |
|---------|---------|
| **spring-boot-starter-test** | JUnit 5, Mockito, AssertJ |
| **Testcontainers** | Docker container management |
| **LocalStack** | Fake AWS services |
| **Awaitility** | Testing async operations |

---

## Dependency Management

### Checking for Updates

```bash
mvn versions:display-dependency-updates
```

### Update Strategy

| Update Type | Example | Action |
|-------------|---------|--------|
| **Patch** | 2.21.0 → 2.21.1 | Update (bug fixes) |
| **Minor** | 2.21.0 → 2.22.0 | Update after testing |
| **Major** | 2.x → 3.x | Careful - breaking changes |
| **Pre-release** | 3.0.0-M1, alpha, RC | Do not use in production |

### What NOT to Update

- **Spring Boot parent** to 4.x (milestone, not stable)
- **SLF4J** to alpha versions
- Dependencies to release candidates (RC) or milestones (M1)

### Managed Dependencies

Many dependencies (Jackson, Logback, etc.) are managed by Spring Boot parent. Don't override these versions unless necessary - Spring Boot ensures compatibility.

### Viewing Dependency Tree

```bash
mvn dependency:tree
```

---

## Development Workflows

### Workflow 1: Writing New Code

```bash
# 1. Write your code in src/main/java/...

# 2. Compile to catch syntax errors quickly
mvn compile

# 3. Write tests in src/test/java/...

# 4. Run tests and check coverage
mvn test
open target/site/jacoco/index.html

# 5. Iterate until coverage is acceptable
```

### Workflow 2: Before Committing

```bash
# Full verification - ensures CI won't fail
mvn clean verify
```

This runs:
1. Compiles code
2. Runs all tests
3. Checks coverage >= 80%
4. Fails if any step fails

### Workflow 3: Debugging a Failing Test

```bash
# Run with full stack traces
mvn test -Dtest=FailingTest -e

# Run in debug mode (attach IDE debugger on port 5005)
mvn test -Dtest=FailingTest -Dmaven.surefire.debug
```

### Workflow 4: Creating a Release

```bash
# 1. Ensure everything passes
mvn clean verify

# 2. Build the final JAR
mvn package

# 3. JAR location:
#    target/uni-secret-manager-spring-boot-starter-1.0.0-SNAPSHOT.jar
```

---

## Quick Reference

| Task | Command |
|------|---------|
| Compile only | `mvn compile` |
| Run all tests | `mvn test` |
| Run one test | `mvn test -Dtest=ClassName` |
| Run one method | `mvn test -Dtest=ClassName#methodName` |
| See coverage | `mvn test && open target/site/jacoco/index.html` |
| Full CI check | `mvn clean verify` |
| Build JAR | `mvn package` |
| Start fresh | `mvn clean` |
| See dependencies | `mvn dependency:tree` |
| Check for updates | `mvn versions:display-dependency-updates` |

---

## IDE Setup

### IntelliJ IDEA

1. Open the project (File → Open → select `pom.xml`)
2. IntelliJ auto-detects Maven and imports dependencies
3. Run tests: Right-click test class → Run
4. Coverage: Right-click → Run with Coverage

### VS Code

1. Install "Extension Pack for Java"
2. Open the project folder
3. VS Code detects Maven automatically
4. Use the Maven panel in the sidebar for commands

---

## Troubleshooting

### Tests fail with "Docker not available"

Testcontainers requires Docker. Verify Docker is running:

```bash
docker ps
```

### Coverage below threshold

1. Run `mvn test` and open the JaCoCo report
2. Click through packages to find uncovered code
3. Add tests for red/yellow highlighted lines
4. Re-run `mvn verify`

### Build takes too long

```bash
# Skip tests for quick builds (development only)
mvn package -DskipTests

# Run only specific tests
mvn test -Dtest=*Unit*
```

### Dependency conflicts

```bash
# View full dependency tree to find conflicts
mvn dependency:tree -Dverbose
```
