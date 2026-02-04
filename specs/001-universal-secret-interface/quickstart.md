# Quickstart: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface

## Overview

This guide shows how to add the Universal Secret Interface to your Spring Boot application and start using secrets from AWS or GCP in under 5 minutes.

---

## Prerequisites

- Java 21+
- Maven 3.8+
- Spring Boot 3.x application
- Access to AWS Secrets Manager or GCP Secret Manager (or use local provider for development)

---

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.timhwang777</groupId>
    <artifactId>uni-secret-manager-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Quick Start (Local Development)

### Step 1: Configure the Local Provider

Add to `application.yml`:

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

### Step 2: Inject Secrets

```java
@Service
public class MyService {

    @SecretValue("database-password")
    private String dbPassword;

    @SecretValue("api-key")
    private String apiKey;

    public void doSomething() {
        System.out.println("Connected with password: " + dbPassword.substring(0, 3) + "***");
    }
}
```

### Step 3: Run Your Application

```bash
mvn spring-boot:run
```

That's it! Your secrets are now injected.

---

## AWS Secrets Manager Setup

### Step 1: Create a Secret in AWS

```bash
aws secretsmanager create-secret \
    --name my-app/database-password \
    --secret-string "super-secret-password"
```

### Step 2: Configure AWS Provider

```yaml
secrets:
  provider-order:
    - aws
  aws:
    enabled: true
    region: us-east-1
```

### Step 3: Set Up Credentials

**Option A: Environment Variables**
```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

**Option B: AWS Profile**
```bash
aws configure --profile myapp
export AWS_PROFILE=myapp
```

**Option C: IAM Role (EC2/ECS/Lambda)**
No configuration needed - credentials are automatic.

### Step 4: Inject Secrets

```java
@Service
public class DatabaseService {

    @SecretValue("my-app/database-password")
    private String dbPassword;
}
```

---

## GCP Secret Manager Setup

### Step 1: Create a Secret in GCP

```bash
echo -n "super-secret-password" | gcloud secrets create my-app-db-password \
    --data-file=- \
    --project=my-project
```

### Step 2: Configure GCP Provider

```yaml
secrets:
  provider-order:
    - gcp
  gcp:
    enabled: true
    project-id: my-project
```

### Step 3: Set Up Credentials

**Option A: Local Development**
```bash
gcloud auth application-default login
```

**Option B: Service Account**
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

**Option C: GKE Workload Identity**
No configuration needed - credentials are automatic.

### Step 4: Inject Secrets

```java
@Service
public class DatabaseService {

    @SecretValue("my-app-db-password")
    private String dbPassword;
}
```

---

## Using JSON Secrets

Secrets can store structured data as JSON:

### AWS Example

Create JSON secret:
```bash
aws secretsmanager create-secret \
    --name my-app/database \
    --secret-string '{"username":"admin","password":"secret123","host":"db.example.com"}'
```

Extract fields:
```java
@Service
public class DatabaseConfig {

    @SecretValue(value = "my-app/database", field = "username")
    private String username;

    @SecretValue(value = "my-app/database", field = "password")
    private String password;

    @SecretValue(value = "my-app/database", field = "host")
    private String host;
}
```

---

## Environment-Specific Configuration

Use Spring profiles to switch providers between environments:

### application-dev.yml
```yaml
secrets:
  provider-order:
    - local
  local:
    enabled: true
    secrets:
      database-password: dev-password
```

### application-prod.yml
```yaml
secrets:
  provider-order:
    - aws
  aws:
    enabled: true
    region: us-east-1
```

Run with profile:
```bash
mvn spring-boot:run -Dspring.profiles.active=prod
```

---

## Using Default Values

For optional secrets:

```java
@Service
public class FeatureService {

    @SecretValue(value = "feature-flag", defaultValue = "disabled")
    private String featureFlag;
}
```

Combined with:
```yaml
secrets:
  fail-on-missing: false  # Don't fail if secret not found
```

---

## Caching Configuration

Secrets are cached by default (5 minutes TTL). Customize:

```yaml
secrets:
  cache:
    enabled: true
    ttl: 10m        # Cache for 10 minutes
    max-size: 500   # Max 500 secrets in cache
```

Disable caching (not recommended for production):
```yaml
secrets:
  cache:
    enabled: false
```

---

## Troubleshooting

### Secret Not Found

```
SecretNotFoundException: Secret 'my-secret' not found in provider 'aws'
```

**Solutions**:
1. Verify secret exists: `aws secretsmanager describe-secret --secret-id my-secret`
2. Check region matches: `secrets.aws.region`
3. Verify IAM permissions

### Authentication Failed

```
SecretProviderException: Access denied for secret 'my-secret'
```

**Solutions**:
1. Check credentials are configured correctly
2. Verify IAM policy allows `secretsmanager:GetSecretValue`
3. For GCP, verify service account has `Secret Manager Secret Accessor` role

### Provider Not Enabled

```
SecretConfigurationException: No secret provider is enabled
```

**Solutions**:
1. Set `secrets.provider-order: [aws]` (or `[gcp]` or `[local]`)
2. Enable the provider: `secrets.aws.enabled: true`

---

## Next Steps

- Read the full [Configuration Reference](./contracts/configuration-schema.md)
- Learn about the [Annotation API](./contracts/annotation-api.md)
- Understand the [Provider Interface](./contracts/provider-interface.md) for custom providers
