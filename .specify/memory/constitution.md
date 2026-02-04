<!--
SYNC IMPACT REPORT
==================
Version change: 0.0.0 → 1.0.0
Bump rationale: Initial constitution creation (MAJOR - first governance establishment)

Modified principles: N/A (initial creation)
Added sections:
  - Core Principles (6 principles)
  - Security Requirements
  - Development Workflow
  - Governance

Templates requiring updates:
  - .specify/templates/plan-template.md: ✅ Compatible (Constitution Check section exists)
  - .specify/templates/spec-template.md: ✅ Compatible (Requirements section supports security)
  - .specify/templates/tasks-template.md: ✅ Compatible (Phase structure supports TDD)

Follow-up TODOs: None
-->

# Uni Secret Manager Spring Constitution

## Core Principles

### I. Security-First

All features and changes MUST prioritize security as a non-negotiable requirement.

- Secrets MUST be encrypted at rest using industry-standard algorithms (AES-256 minimum)
- Secrets MUST be encrypted in transit (TLS 1.2+ required)
- All access to secrets MUST be logged with audit trails (who, what, when)
- Authentication and authorization MUST be enforced on all endpoints
- No secrets or sensitive data in logs, error messages, or stack traces
- Security vulnerabilities MUST be addressed before any feature work
- Dependency vulnerabilities MUST be scanned and remediated regularly

**Rationale**: A secret manager that is not secure is worse than no secret manager at all.

### II. Test-Driven Development

TDD is mandatory for all feature implementation.

- Tests MUST be written before implementation code
- Test cycle: Red (failing test) → Green (minimal passing code) → Refactor
- Unit tests MUST cover business logic with >80% line coverage
- Integration tests MUST cover API endpoints and database interactions
- Security-related code MUST have dedicated test coverage
- No PR merges with failing tests

**Rationale**: TDD ensures correctness, prevents regressions, and forces clear API design before implementation.

### III. API-First Design

All functionality MUST be exposed through well-designed RESTful APIs.

- OpenAPI/Swagger documentation MUST be maintained for all endpoints
- API versioning MUST be used (e.g., `/api/v1/`)
- Request/response contracts MUST be defined before implementation
- APIs MUST follow REST conventions (proper HTTP methods, status codes)
- Error responses MUST be consistent and machine-readable
- Breaking changes MUST increment the API version

**Rationale**: API-first ensures clear contracts, enables parallel frontend/backend development, and supports future integrations.

### IV. Spring Best Practices

Follow Spring Boot conventions and idioms consistently.

- Use constructor injection for dependencies (no field injection)
- Externalize configuration via `application.yml` and profiles
- Use Spring Security for authentication/authorization
- Leverage Spring Data repositories for data access
- Use proper exception handling with `@ControllerAdvice`
- Follow layered architecture: Controller → Service → Repository
- Use Spring's validation annotations for input validation

**Rationale**: Consistency with Spring conventions improves maintainability and reduces onboarding friction.

### V. KISS Principle (Keep It Simple)

Favor simplicity over cleverness or premature optimization.

- Start with the simplest solution that meets requirements
- Avoid abstractions until patterns repeat at least twice
- No premature optimization; profile before optimizing
- Prefer standard library solutions over custom implementations
- One class, one responsibility; one method, one purpose
- Delete code that is no longer needed; don't comment it out

**Rationale**: Simple code is easier to understand, test, maintain, and extend.

### VI. Extensibility

Design for future extension without modification of existing code.

- Use interfaces for service contracts to allow alternative implementations
- Configuration-driven behavior where appropriate
- Support for multiple secret backends (database, Vault, AWS, etc.) via adapters
- Plugin architecture for authentication providers
- Event-driven hooks for audit and notification integrations
- Document extension points clearly

**Rationale**: A secret manager must adapt to evolving organizational needs without breaking existing functionality.

## Security Requirements

All development MUST adhere to these security standards:

- **Authentication**: JWT or OAuth 2.0 with secure token storage
- **Authorization**: Role-based access control (RBAC) with principle of least privilege
- **Data Protection**: AES-256 encryption for secrets at rest; TLS 1.2+ in transit
- **Audit Logging**: Immutable logs for all secret access and modifications
- **Input Validation**: Whitelist validation on all user inputs
- **Dependency Management**: Regular security scans; no known vulnerabilities in production
- **Secret Rotation**: Support for automatic secret rotation and expiration

## Development Workflow

### Code Review Standards

- All changes MUST be reviewed by at least one other developer
- Security-sensitive changes MUST be reviewed by a designated security reviewer
- Review checklist:
  - [ ] Tests pass and coverage meets thresholds
  - [ ] No security vulnerabilities introduced
  - [ ] API contracts maintained or versioned appropriately
  - [ ] Documentation updated if public API changed
  - [ ] No secrets or sensitive data in code/logs

### Security Review Process

- All PRs touching authentication, authorization, encryption, or secret handling MUST undergo security review
- Security reviewer MUST verify:
  - [ ] No new attack vectors introduced
  - [ ] Encryption properly implemented
  - [ ] Access controls enforced
  - [ ] Audit logging in place
  - [ ] Input validation complete

### Documentation Requirements

- OpenAPI specs MUST be updated for all API changes
- Architecture Decision Records (ADRs) for significant design decisions
- README with setup instructions and quickstart guide
- Runbooks for operational procedures (deployment, incident response)

## Governance

This constitution supersedes all other development practices for this project.

**Amendment Process**:
1. Proposed amendments MUST be documented with rationale
2. Changes require approval from project maintainers
3. Migration plan required for breaking changes to existing code
4. Version increment follows semantic versioning (see below)

**Versioning Policy**:
- MAJOR: Removal or redefinition of core principles
- MINOR: New principle or section added; material expansion of guidance
- PATCH: Clarifications, wording improvements, typo fixes

**Compliance**:
- All PRs MUST verify compliance with this constitution
- Constitution Check in implementation plans MUST pass before development begins
- Violations MUST be justified in writing and approved by maintainers

**Version**: 1.0.0 | **Ratified**: 2026-01-16 | **Last Amended**: 2026-01-16
