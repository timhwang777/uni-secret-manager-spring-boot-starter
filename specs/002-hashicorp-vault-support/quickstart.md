# Quickstart: HashiCorp Vault Secret Provider

**Feature**: 002-hashicorp-vault-support

## Prerequisites

- HashiCorp Vault server (v1.12+) running and accessible
- The `uni-secret-manager-spring-boot-starter` dependency in your project

## Step 1: Add Dependency

The `spring-vault-core` dependency is included transitively via the starter. No additional dependencies needed.

## Step 2: Configure Vault Provider

Add the following to your `application.yml`:

### Simplest Configuration (Token Auth)

```yaml
secrets:
  provider-order: [vault]
  vault:
    enabled: true
    host: localhost
    port: 8200
    scheme: http          # Use https in production
    token: my-vault-token
```

### AppRole Authentication (Recommended for Production)

```yaml
secrets:
  provider-order: [vault, local]
  vault:
    enabled: true
    host: vault.internal.company.com
    auth-method: APPROLE
    app-role:
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
    ssl:
      ca-cert-path: /etc/ssl/vault-ca.pem
```

### Kubernetes Authentication

```yaml
secrets:
  provider-order: [vault]
  vault:
    enabled: true
    host: vault.vault.svc.cluster.local
    auth-method: KUBERNETES
    kubernetes:
      role: my-app-role
```

## Step 3: Store a Secret in Vault

```bash
# Using Vault CLI
vault kv put secret/my-app/database username=admin password=s3cret
```

## Step 4: Retrieve Secrets

### Using @SecretValue Annotation

```java
@Component
public class MyService {

    @SecretValue(value = "my-app/database", field = "password")
    private String dbPassword;

    @SecretValue(value = "my-app/database")
    private String dbConfig;  // Returns full JSON: {"username":"admin","password":"s3cret"}
}
```

### Using SecretResolver Programmatically

```java
@Service
public class MyService {
    private final SecretResolver secretResolver;

    public MyService(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    public void doSomething() {
        // Get full secret as JSON
        String secret = secretResolver.resolve("my-app/database");

        // Get specific version
        String v2 = secretResolver.resolve(
            SecretReference.builder()
                .key("my-app/database")
                .version("2")
                .build()
        );

        // Get specific field from JSON
        String password = secretResolver.resolve(
            SecretReference.builder()
                .key("my-app/database")
                .field("password")
                .build()
        );
    }
}
```

## Step 5: Use with Fallback Chain

```yaml
secrets:
  provider-order: [vault, local]
  vault:
    enabled: true
    host: vault.company.com
    auth-method: APPROLE
    app-role:
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
  local:
    enabled: true
    secrets:
      my-app/database: '{"username":"dev","password":"devpass"}'
```

In this setup, the resolver tries Vault first and falls back to Local if the secret is not found in Vault.

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `secrets.vault.enabled` | `false` | Enable Vault provider |
| `secrets.vault.host` | `localhost` | Vault server host |
| `secrets.vault.port` | `8200` | Vault server port |
| `secrets.vault.scheme` | `https` | `http` or `https` |
| `secrets.vault.namespace` | — | Vault Enterprise namespace |
| `secrets.vault.auth-method` | `TOKEN` | `TOKEN`, `APPROLE`, or `KUBERNETES` |
| `secrets.vault.token` | — | Static token (TOKEN auth) |
| `secrets.vault.mount` | `secret` | KV engine mount path |
| `secrets.vault.kv-version` | `2` | KV engine version (1 or 2) |
| `secrets.vault.app-role.role-id` | — | AppRole role ID |
| `secrets.vault.app-role.secret-id` | — | AppRole secret ID |
| `secrets.vault.app-role.path` | `approle` | AppRole auth mount path |
| `secrets.vault.kubernetes.role` | — | K8s auth role |
| `secrets.vault.kubernetes.service-account-token-path` | `/var/run/secrets/kubernetes.io/serviceaccount/token` | SA JWT path |
| `secrets.vault.kubernetes.path` | `kubernetes` | K8s auth mount path |
| `secrets.vault.ssl.ca-cert-path` | — | PEM CA cert file path |

## Troubleshooting

**"Vault provider is not enabled"**: Set `secrets.vault.enabled: true` in your configuration.

**"Token must not be blank"**: When using TOKEN auth, provide `secrets.vault.token`.

**"Connection refused"**: Verify `host`, `port`, and `scheme` match your Vault server. Ensure Vault is unsealed.

**SSL certificate errors**: For self-signed certs, set `secrets.vault.ssl.ca-cert-path` to your CA PEM file.

**Permission denied**: Verify your Vault token/role has read access to the KV mount path (`secret/data/*` for KV v2).
