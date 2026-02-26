package io.github.timhwang777.unisecret.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.VaultSecretProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for VaultSecretProvider using a real Vault container.
 * Tests KV v2 secret retrieval, versioning, and error handling.
 */
@Testcontainers
class VaultIntegrationTest {

    private static final String VAULT_TOKEN = "test-root-token";
    private static final String VAULT_IMAGE = "hashicorp/vault:1.16.3";

    @Container
    static final VaultContainer<?> vault = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
            .withVaultToken(VAULT_TOKEN)
            .withSecretInVault("secret/my-app", "username=admin", "password=s3cret")
            .withSecretInVault("secret/single-key", "value=hello");

    private static VaultSecretProvider provider;
    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void setUp() throws Exception {
        VaultEndpoint endpoint = VaultEndpoint.create(vault.getHost(), vault.getMappedPort(8200));
        endpoint.setScheme("http");

        vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(VAULT_TOKEN));

        SecretManagerProperties.Vault vaultProps = new SecretManagerProperties.Vault();
        vaultProps.setEnabled(true);
        vaultProps.setHost(vault.getHost());
        vaultProps.setPort(vault.getMappedPort(8200));
        vaultProps.setScheme("http");
        vaultProps.setToken(VAULT_TOKEN);
        vaultProps.setMount("secret");
        vaultProps.setKvVersion(2);

        provider = new VaultSecretProvider(vaultTemplate, vaultProps, new ObjectMapper());

        // Write versioned secrets for T008 / versioning tests
        vault.execInContainer("vault", "kv", "put", "secret/versioned", "val=version-one");
        vault.execInContainer("vault", "kv", "put", "secret/versioned", "val=version-two");
        vault.execInContainer("vault", "kv", "put", "secret/versioned", "val=version-three");
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers manages container lifecycle; no explicit cleanup needed
    }

    // ==================== T014: KV v2 retrieval ====================

    @Test
    void shouldRetrieveSecretAsJsonFromKvV2() throws Exception {
        Optional<String> result = provider.getSecret("my-app");

        assertThat(result).isPresent();
        String json = result.get();
        assertThat(json).contains("username");
        assertThat(json).contains("admin");
        assertThat(json).contains("password");
        assertThat(json).contains("s3cret");

        // Verify it is valid JSON
        new ObjectMapper().readTree(json);
    }

    @Test
    void shouldReturnEmptyForNonExistentSecret() {
        Optional<String> result = provider.getSecret("does-not-exist");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnCorrectProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.VAULT);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    // ==================== Versioning tests ====================

    @Test
    void shouldRetrieveLatestVersionByDefault() {
        Optional<String> result = provider.getSecret("versioned");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("version-three");
    }

    @Test
    void shouldRetrieveSpecificVersionOne() {
        Optional<String> result = provider.getSecret("versioned", "1");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("version-one");
    }

    @Test
    void shouldRetrieveSpecificVersionTwo() {
        Optional<String> result = provider.getSecret("versioned", "2");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("version-two");
    }

    @Test
    void shouldReturnEmptyForNonExistentVersion() {
        Optional<String> result = provider.getSecret("versioned", "999");
        assertThat(result).isEmpty();
    }

    // ==================== T022: AppRole authentication ====================

    @Test
    void shouldAuthenticateWithAppRoleAndRetrieveSecret() throws Exception {
        // Enable AppRole auth and create a role in the Vault container
        vault.execInContainer("vault", "auth", "enable", "approle");
        vault.execInContainer("vault", "policy", "write", "read-secrets",
                "-", "<< EOF\npath \"secret/*\" { capabilities = [\"read\",\"list\"] }\nEOF");
        vault.execInContainer("vault", "write", "auth/approle/role/test-role",
                "token_policies=default,read-secrets");

        var roleIdResult = vault.execInContainer("vault", "read",
                "-field=role_id", "auth/approle/role/test-role/role-id");
        var secretIdResult = vault.execInContainer("vault", "write",
                "-f", "-field=secret_id", "auth/approle/role/test-role/secret-id");

        String roleId = roleIdResult.getStdout().trim();
        String secretId = secretIdResult.getStdout().trim();

        assertThat(roleId).isNotBlank();
        assertThat(secretId).isNotBlank();

        // Build AppRole-authenticated provider
        org.springframework.vault.authentication.AppRoleAuthenticationOptions options =
                org.springframework.vault.authentication.AppRoleAuthenticationOptions.builder()
                        .roleId(org.springframework.vault.authentication.AppRoleAuthenticationOptions
                                .RoleId.provided(roleId))
                        .secretId(org.springframework.vault.authentication.AppRoleAuthenticationOptions
                                .SecretId.provided(secretId))
                        .build();

        VaultEndpoint endpoint = VaultEndpoint.create(vault.getHost(), vault.getMappedPort(8200));
        endpoint.setScheme("http");

        var requestFactory = org.springframework.vault.client.ClientHttpRequestFactoryFactory.create(
                new org.springframework.vault.support.ClientOptions(),
                org.springframework.vault.support.SslConfiguration.unconfigured());
        org.springframework.web.client.RestTemplate restTemplate =
                org.springframework.vault.client.VaultClients.createRestTemplate(endpoint, requestFactory);
        var appRoleAuth = new org.springframework.vault.authentication.AppRoleAuthentication(
                options, restTemplate);
        VaultTemplate appRoleTemplate = new VaultTemplate(endpoint, appRoleAuth);

        SecretManagerProperties.Vault appRoleProps = new SecretManagerProperties.Vault();
        appRoleProps.setEnabled(true);
        appRoleProps.setHost(vault.getHost());
        appRoleProps.setPort(vault.getMappedPort(8200));
        appRoleProps.setScheme("http");
        appRoleProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.APPROLE);
        appRoleProps.getAppRole().setRoleId(roleId);
        appRoleProps.getAppRole().setSecretId(secretId);
        appRoleProps.setMount("secret");
        appRoleProps.setKvVersion(2);

        VaultSecretProvider appRoleProvider = new VaultSecretProvider(
                appRoleTemplate, appRoleProps, new ObjectMapper());

        Optional<String> result = appRoleProvider.getSecret("my-app");
        assertThat(result).isPresent();
        assertThat(result.get()).contains("admin");
    }

    @Test
    void shouldThrowExceptionOnPermissionDenied() {
        // Use an invalid token that has no permissions
        VaultEndpoint endpoint = VaultEndpoint.create(vault.getHost(), vault.getMappedPort(8200));
        endpoint.setScheme("http");
        VaultTemplate noPermTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication("invalid-token-no-permissions"));

        SecretManagerProperties.Vault noPermProps = new SecretManagerProperties.Vault();
        noPermProps.setEnabled(true);
        noPermProps.setHost(vault.getHost());
        noPermProps.setToken("invalid-token-no-permissions");
        noPermProps.setMount("secret");
        noPermProps.setKvVersion(2);

        VaultSecretProvider noPermProvider = new VaultSecretProvider(
                noPermTemplate, noPermProps, new ObjectMapper());

        assertThatThrownBy(() -> noPermProvider.getSecret("my-app"))
                .isInstanceOf(SecretProviderException.class)
                .satisfies(e -> assertThat(((SecretProviderException) e).isRetryable()).isFalse());
    }
}
