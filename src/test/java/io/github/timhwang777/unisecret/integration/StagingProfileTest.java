package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring profile-based provider configuration - Staging Profile.
 */
@SpringBootTest(classes = {
        StagingProfileTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@ActiveProfiles("staging")
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.local.enabled=true",
        "secrets.local.secrets.test-secret=staging-value",
        "secrets.aws.enabled=false",
        "secrets.gcp.enabled=false"
})
class StagingProfileTest {

    @Configuration
    @Import(SecretManagerAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired(required = false)
    private LocalSecretProvider localProvider;

    @Autowired(required = false)
    private SecretManagerProperties properties;

    @Autowired(required = false)
    private SecretResolver resolver;

    @Test
    void shouldEnableLocalProviderInStagingProfile() {
        assertThat(localProvider).isNotNull();
        assertThat(localProvider.isEnabled()).isTrue();
    }

    @Test
    void shouldConfigureMixedProvidersInStagingProfile() {
        assertThat(properties).isNotNull();
        assertThat(properties.getProviderOrder()).containsExactly("local");
    }

    @Test
    void shouldResolveSecretsInStagingProfile() {
        assertThat(resolver).isNotNull();
        String secret = resolver.resolve("test-secret");
        assertThat(secret).isEqualTo("staging-value");
    }
}
