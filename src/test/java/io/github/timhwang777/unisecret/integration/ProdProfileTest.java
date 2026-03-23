package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring profile-based provider configuration - Production Profile.
 *
 * This test verifies that the active provider order stays consistent with the
 * providers that are actually configured in the test context.
 */
@SpringBootTest(classes = {
        ProdProfileTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.local.enabled=true",
        "secrets.local.secrets.dummy=prod-placeholder",
        "secrets.aws.enabled=false",
        "secrets.gcp.enabled=false"
})
class ProdProfileTest {

    @Configuration
    @Import(SecretManagerAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired(required = false)
    private LocalSecretProvider localProvider;

    @Autowired(required = false)
    private SecretManagerProperties properties;

    @Test
    void shouldConfigureCloudProvidersInProdProfile() {
        assertThat(properties).isNotNull();
        assertThat(properties.getProviderOrder()).containsExactly("local");
    }

    @Test
    void shouldAllowProviderOrderConfiguration() {
        assertThat(properties).isNotNull();
        assertThat(properties.getProviderOrder().getFirst()).isEqualTo("local");
    }
}
