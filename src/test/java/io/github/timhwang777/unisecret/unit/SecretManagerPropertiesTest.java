package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SecretManagerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Configuration
    @EnableConfigurationProperties(SecretManagerProperties.class)
    static class TestConfiguration {
    }

    @Test
    void shouldUseDefaultValues() {
        contextRunner.run(context -> {
            SecretManagerProperties properties = context.getBean(SecretManagerProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getProviderOrder()).containsExactly("aws", "gcp", "local");
            assertThat(properties.isFailOnMissing()).isTrue();

            assertThat(properties.getAws().isEnabled()).isFalse();
            assertThat(properties.getGcp().isEnabled()).isFalse();
            assertThat(properties.getGcp().getDefaultVersion()).isEqualTo("latest");
            assertThat(properties.getLocal().isEnabled()).isFalse();

            assertThat(properties.getCache().isEnabled()).isTrue();
            assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getCache().getMaxSize()).isEqualTo(1000);

            assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getRetry().getInitialDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getRetry().getMultiplier()).isEqualTo(2.0);
            assertThat(properties.getRetry().getMaxDelay()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void shouldUseVaultDefaultValues() {
        contextRunner.run(context -> {
            SecretManagerProperties properties = context.getBean(SecretManagerProperties.class);
            SecretManagerProperties.Vault vault = properties.getVault();

            assertThat(vault.isEnabled()).isFalse();
            assertThat(vault.getHost()).isEqualTo("localhost");
            assertThat(vault.getPort()).isEqualTo(8200);
            assertThat(vault.getScheme()).isEqualTo("https");
            assertThat(vault.getNamespace()).isNull();
            assertThat(vault.getAuthMethod()).isEqualTo(SecretManagerProperties.Vault.AuthMethod.TOKEN);
            assertThat(vault.getToken()).isNull();
            assertThat(vault.getMount()).isEqualTo("secret");
            assertThat(vault.getKvVersion()).isEqualTo(2);
            assertThat(vault.getAppRole().getPath()).isEqualTo("approle");
            assertThat(vault.getKubernetes().getPath()).isEqualTo("kubernetes");
            assertThat(vault.getKubernetes().getServiceAccountTokenPath())
                    .isEqualTo("/var/run/secrets/kubernetes.io/serviceaccount/token");
            assertThat(vault.getSsl().getCaCertPath()).isNull();
        });
    }

    @Test
    void shouldBindCustomVaultConfiguration() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=vault.internal.co",
                        "secrets.vault.port=8300",
                        "secrets.vault.scheme=https",
                        "secrets.vault.namespace=team-a",
                        "secrets.vault.auth-method=APPROLE",
                        "secrets.vault.mount=myapp",
                        "secrets.vault.kv-version=1",
                        "secrets.vault.app-role.role-id=rid",
                        "secrets.vault.app-role.secret-id=sid",
                        "secrets.vault.app-role.path=custom-approle",
                        "secrets.vault.kubernetes.role=myrole",
                        "secrets.vault.kubernetes.path=custom-k8s",
                        "secrets.vault.ssl.ca-cert-path=/etc/ssl/ca.pem"
                )
                .run(context -> {
                    SecretManagerProperties properties = context.getBean(SecretManagerProperties.class);
                    SecretManagerProperties.Vault vault = properties.getVault();

                    assertThat(vault.isEnabled()).isTrue();
                    assertThat(vault.getHost()).isEqualTo("vault.internal.co");
                    assertThat(vault.getPort()).isEqualTo(8300);
                    assertThat(vault.getScheme()).isEqualTo("https");
                    assertThat(vault.getNamespace()).isEqualTo("team-a");
                    assertThat(vault.getAuthMethod())
                            .isEqualTo(SecretManagerProperties.Vault.AuthMethod.APPROLE);
                    assertThat(vault.getMount()).isEqualTo("myapp");
                    assertThat(vault.getKvVersion()).isEqualTo(1);
                    assertThat(vault.getAppRole().getRoleId()).isEqualTo("rid");
                    assertThat(vault.getAppRole().getSecretId()).isEqualTo("sid");
                    assertThat(vault.getAppRole().getPath()).isEqualTo("custom-approle");
                    assertThat(vault.getKubernetes().getRole()).isEqualTo("myrole");
                    assertThat(vault.getKubernetes().getPath()).isEqualTo("custom-k8s");
                    assertThat(vault.getSsl().getCaCertPath()).isEqualTo("/etc/ssl/ca.pem");
                });
    }

    @Test
    void shouldNotChangeDefaultProviderOrderWhenVaultAdded() {
        contextRunner.run(context -> {
            SecretManagerProperties properties = context.getBean(SecretManagerProperties.class);
            // Vault must NOT be in the default provider-order (users opt-in explicitly)
            assertThat(properties.getProviderOrder()).containsExactly("aws", "gcp", "local");
            assertThat(properties.getProviderOrder()).doesNotContain("vault");
        });
    }

    @Test
    void shouldBindCustomConfiguration() {
        contextRunner
                .withPropertyValues(
                        "secrets.enabled=false",
                        "secrets.provider-order=gcp,local",
                        "secrets.fail-on-missing=false",
                        "secrets.aws.enabled=true",
                        "secrets.aws.region=us-west-2",
                        "secrets.gcp.enabled=true",
                        "secrets.gcp.project-id=my-project",
                        "secrets.local.enabled=true",
                        "secrets.cache.ttl=10m",
                        "secrets.retry.max-attempts=5"
                )
                .run(context -> {
                    SecretManagerProperties properties = context.getBean(SecretManagerProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getProviderOrder()).containsExactly("gcp", "local");
                    assertThat(properties.isFailOnMissing()).isFalse();

                    assertThat(properties.getAws().isEnabled()).isTrue();
                    assertThat(properties.getAws().getRegion()).isEqualTo("us-west-2");

                    assertThat(properties.getGcp().isEnabled()).isTrue();
                    assertThat(properties.getGcp().getProjectId()).isEqualTo("my-project");

                    assertThat(properties.getLocal().isEnabled()).isTrue();

                    assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
                });
    }
}
