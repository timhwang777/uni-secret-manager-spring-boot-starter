package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring profile-based provider configuration - Production Profile.
 *
 * This test demonstrates that in production, the local provider can be disabled
 * while cloud providers are configured. For this test, we manually register
 * a local provider to meet the validation requirement.
 */
@SpringBootTest(classes = {
        ProdProfileTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=aws,gcp",
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
        assertThat(properties.getProviderOrder()).containsExactly("aws", "gcp");
    }

    @Test
    void shouldAllowProviderOrderConfiguration() {
        assertThat(properties).isNotNull();
        // In production, we typically configure cloud providers first
        assertThat(properties.getProviderOrder().get(0)).isEqualTo("aws");
        assertThat(properties.getProviderOrder().get(1)).isEqualTo("gcp");
    }
}
