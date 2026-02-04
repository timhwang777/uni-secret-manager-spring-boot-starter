package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for LocalSecretProvider.
 */
@SpringBootTest(classes = LocalProviderIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "secrets.local.enabled=true",
        "secrets.local.test-secret=test-value",
        "secrets.local.database-password=db-pass-123",
        "app-api-key=app-key-value"
})
class LocalProviderIntegrationTest {

    @Configuration
    static class TestConfig {

        @Bean
        public SecretManagerProperties secretManagerProperties() {
            SecretManagerProperties properties = new SecretManagerProperties();
            SecretManagerProperties.Local local = new SecretManagerProperties.Local();
            local.setEnabled(true);

            Map<String, String> secrets = new HashMap<>();
            secrets.put("map-secret", "map-value");
            local.setSecrets(secrets);

            properties.setLocal(local);
            return properties;
        }

        @Bean
        public LocalSecretProvider localSecretProvider(Environment environment, SecretManagerProperties properties) {
            return new LocalSecretProvider(environment, properties.getLocal());
        }
    }

    @Autowired
    private LocalSecretProvider provider;

    @Test
    void shouldRetrieveSecretFromProperties() {
        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("test-value");
    }

    @Test
    void shouldRetrieveSecretFromMap() {
        Optional<String> result = provider.getSecret("map-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("map-value");
    }

    @Test
    void shouldRetrieveSecretFromEnvironmentWithoutPrefix() {
        Optional<String> result = provider.getSecret("app-api-key");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("app-key-value");
    }

    @Test
    void shouldReturnEmptyForNonExistentSecret() {
        Optional<String> result = provider.getSecret("non-existent-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnCorrectProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.LOCAL);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldValidateConfiguration() {
        provider.validateConfiguration();
        // Should not throw any exception
    }
}
