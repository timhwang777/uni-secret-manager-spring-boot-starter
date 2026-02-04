# Configuration Contract: Universal Secret Interface

**Date**: 2026-01-16
**Feature**: 001-universal-secret-interface
**Updated**: Multi-provider fallback chain support

## Overview

This document defines the configuration contract for the Universal Secret Interface Spring Boot starter. All configuration is done via standard Spring Boot mechanisms (`application.yml`, `application.properties`, environment variables).

---

## Configuration Namespace

All properties are prefixed with `secrets.`

---

## Root Configuration

```yaml
secrets:
  enabled: true                    # boolean, default: true
  provider-order:                  # list, default: [aws, gcp, local]
    - aws
    - gcp
    - local
  fail-on-missing: true            # boolean, default: true
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.enabled` | boolean | No | `true` | - |
| `secrets.provider-order` | List<String> | No | `[aws, gcp, local]` | Each value must be: `aws`, `gcp`, or `local` |
| `secrets.fail-on-missing` | boolean | No | `true` | - |

**Provider Order Behavior**:
- Providers are tried in the order listed
- Only enabled providers are actually queried
- First provider to return a value wins
- If all providers are exhausted, `SecretNotFoundException` is thrown

---

## AWS Provider Configuration

```yaml
secrets:
  aws:
    enabled: true                  # boolean, default: false
    region: us-east-1              # string, optional
    endpoint: http://localhost:4566  # string, optional (for LocalStack)
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.aws.enabled` | boolean | No | `false` | - |
| `secrets.aws.region` | string | No | SDK default | Valid AWS region code |
| `secrets.aws.endpoint` | string | No | SDK default | Valid URL |

**Authentication**: Uses AWS SDK default credential chain:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. System properties
3. AWS profile (~/.aws/credentials)
4. IAM role (EC2/ECS/Lambda)

---

## GCP Provider Configuration

```yaml
secrets:
  gcp:
    enabled: true                  # boolean, default: false
    project-id: my-gcp-project     # string, optional
    default-version: latest        # string, default: "latest"
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.gcp.enabled` | boolean | No | `false` | - |
| `secrets.gcp.project-id` | string | No | ADC default | Valid GCP project ID |
| `secrets.gcp.default-version` | string | No | `"latest"` | Version number or "latest" |

**Authentication**: Uses Application Default Credentials (ADC):
1. `GOOGLE_APPLICATION_CREDENTIALS` environment variable
2. `gcloud auth application-default login`
3. Service account (GKE/Compute Engine)

---

## Local Provider Configuration

```yaml
secrets:
  local:
    enabled: true                  # boolean, default: false
    database-password: dev-pass    # dynamic key-value pairs
    api-key: dev-key
    nested/secret: nested-value
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.local.enabled` | boolean | No | `false` | - |
| `secrets.local.*` | string | Dynamic | - | Any secret key-value pair |

**Note**: Secret keys can use `/` or `.` as path separators. Both `secrets.local.database/password` and `secrets.local.database.password` are valid.

---

## Cache Configuration

```yaml
secrets:
  cache:
    enabled: true                  # boolean, default: true
    ttl: 5m                        # duration, default: 5m
    max-size: 1000                 # int, default: 1000
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.cache.enabled` | boolean | No | `true` | - |
| `secrets.cache.ttl` | Duration | No | `5m` | Positive duration |
| `secrets.cache.max-size` | int | No | `1000` | Positive integer |

**Duration Format**: Spring Boot duration format (e.g., `5m`, `300s`, `PT5M`)

**Cache Behavior with Multi-Provider**:
- Cache stores the final resolved value (not per-provider results)
- Cache key is `{secretKey}:{version}:{field}` (provider not included)
- On cache miss, the entire provider chain is executed

---

## Retry Configuration

```yaml
secrets:
  retry:
    max-attempts: 3                # int, default: 3
    initial-delay: 1s              # duration, default: 1s
    multiplier: 2.0                # double, default: 2.0
    max-delay: 10s                 # duration, default: 10s
```

| Property | Type | Required | Default | Validation |
|----------|------|----------|---------|------------|
| `secrets.retry.max-attempts` | int | No | `3` | >= 1 |
| `secrets.retry.initial-delay` | Duration | No | `1s` | Positive duration |
| `secrets.retry.multiplier` | double | No | `2.0` | >= 1.0 |
| `secrets.retry.max-delay` | Duration | No | `10s` | Positive duration |

**Retry Behavior with Multi-Provider**:
- Retries apply per-provider (each provider gets `max-attempts` tries)
- After exhausting retries, the next provider in the chain is tried
- Exponential backoff: delay = min(initial-delay * multiplier^attempt, max-delay)

---

## Complete Examples

### Multi-Provider Fallback (Production)

```yaml
secrets:
  provider-order:
    - aws      # Primary
    - gcp      # Fallback
  fail-on-missing: true

  aws:
    enabled: true
    region: us-east-1

  gcp:
    enabled: true
    project-id: my-production-project

  cache:
    enabled: true
    ttl: 5m
    max-size: 500

  retry:
    max-attempts: 3
    initial-delay: 1s
    multiplier: 2.0
```

### Single Provider (AWS Only)

```yaml
secrets:
  provider-order:
    - aws
  fail-on-missing: true

  aws:
    enabled: true
    region: us-east-1

  cache:
    enabled: true
    ttl: 5m
```

### Local Development with Cloud Fallback

```yaml
secrets:
  provider-order:
    - local    # Fast, no network
    - aws      # Fallback for secrets not overridden locally
  fail-on-missing: false

  local:
    enabled: true
    database-password: dev-password
    api-key: dev-api-key

  aws:
    enabled: true
    region: us-east-1
    endpoint: http://localhost:4566  # LocalStack

  cache:
    enabled: false  # Disable cache in dev
```

### Hybrid Cloud (GCP Primary, AWS Legacy)

```yaml
secrets:
  provider-order:
    - gcp      # Our main cloud
    - aws      # Legacy secrets still in AWS
    - local    # Emergency overrides
  fail-on-missing: true

  gcp:
    enabled: true
    project-id: production-project

  aws:
    enabled: true
    region: us-west-2

  local:
    enabled: true
    # Empty - only used for emergency overrides via env vars
```

### Testing with LocalStack

```yaml
secrets:
  provider-order:
    - aws
  fail-on-missing: true

  aws:
    enabled: true
    region: us-east-1
    endpoint: http://localhost:4566

  cache:
    enabled: false
```

---

## Environment Variable Mapping

All properties can be set via environment variables using Spring Boot's relaxed binding:

| Property | Environment Variable |
|----------|---------------------|
| `secrets.provider-order[0]` | `SECRETS_PROVIDERORDER_0` |
| `secrets.provider-order[1]` | `SECRETS_PROVIDERORDER_1` |
| `secrets.aws.enabled` | `SECRETS_AWS_ENABLED` |
| `secrets.aws.region` | `SECRETS_AWS_REGION` |
| `secrets.gcp.enabled` | `SECRETS_GCP_ENABLED` |
| `secrets.gcp.project-id` | `SECRETS_GCP_PROJECTID` |
| `secrets.local.enabled` | `SECRETS_LOCAL_ENABLED` |
| `secrets.local.my-secret` | `SECRETS_LOCAL_MYSECRET` |
| `secrets.cache.ttl` | `SECRETS_CACHE_TTL` |

---

## Validation Errors

| Error Code | Message | Resolution |
|------------|---------|------------|
| `SECRETS_001` | No providers enabled | Enable at least one provider |
| `SECRETS_002` | Invalid provider in provider-order | Use `aws`, `gcp`, or `local` |
| `SECRETS_003` | AWS region required | Set `secrets.aws.region` or `AWS_REGION` |
| `SECRETS_004` | GCP project ID required | Set `secrets.gcp.project-id` or use ADC |
| `SECRETS_005` | Provider in order but not enabled | Enable the provider or remove from order |

---

## Provider Order vs Enabled

The `provider-order` list defines the fallback sequence, but only **enabled** providers are actually used.

**Example**:
```yaml
secrets:
  provider-order: [aws, gcp, local]  # Defined order

  aws:
    enabled: true   # ✅ Will be tried 1st
  gcp:
    enabled: false  # ❌ Skipped (disabled)
  local:
    enabled: true   # ✅ Will be tried 2nd
```

Effective chain: `aws → local` (gcp skipped because disabled)

This design allows:
- Defining a standard order across environments
- Enabling/disabling providers per environment via profiles or env vars
- No need to change `provider-order` when toggling providers
