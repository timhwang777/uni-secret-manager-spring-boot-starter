package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalSecretProviderTest {

    @Mock
    private Environment environment;

    private SecretManagerProperties.Local localProperties;
    private LocalSecretProvider provider;

    @BeforeEach
    void setUp() {
        localProperties = new SecretManagerProperties.Local();
        localProperties.setEnabled(true);

        Map<String, String> secrets = new HashMap<>();
        secrets.put("test-secret", "test-value");
        secrets.put("database-password", "db-pass-123");
        localProperties.setSecrets(secrets);

        provider = new LocalSecretProvider(environment, localProperties);
    }

    @Test
    void shouldReturnProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.LOCAL);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldBeDisabled() {
        localProperties.setEnabled(false);
        provider = new LocalSecretProvider(environment, localProperties);

        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void shouldRetrieveSecretFromMap() {
        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("test-value");
    }

    @Test
    void shouldRetrieveSecretFromEnvironmentWithPrefix() {
        when(environment.getProperty("secrets.local.env-secret"))
                .thenReturn("env-value");

        Optional<String> result = provider.getSecret("env-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("env-value");
    }

    @Test
    void shouldRetrieveSecretFromEnvironmentWithoutPrefix() {
        when(environment.getProperty("secrets.local.plain-secret"))
                .thenReturn(null);
        when(environment.getProperty("plain-secret"))
                .thenReturn("plain-value");

        Optional<String> result = provider.getSecret("plain-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("plain-value");
    }

    @Test
    void shouldReturnEmptyWhenSecretNotFound() {
        when(environment.getProperty(anyString())).thenReturn(null);

        Optional<String> result = provider.getSecret("non-existent-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPreferMapOverEnvironment() {
        lenient().when(environment.getProperty("secrets.local.test-secret"))
                .thenReturn("env-value");

        Optional<String> result = provider.getSecret("test-secret");

        // Should return value from map, not environment
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("test-value");
    }

    @Test
    void shouldIgnoreVersionParameter() {
        // Local provider ignores version parameter
        Optional<String> result = provider.getSecret("test-secret", "v1");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("test-value");
    }

    @Test
    void shouldReturnEmptyForEmptyValue() {
        Map<String, String> secrets = new HashMap<>();
        secrets.put("empty-secret", "");
        localProperties.setSecrets(secrets);
        provider = new LocalSecretProvider(environment, localProperties);

        Optional<String> result = provider.getSecret("empty-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldValidateConfiguration() {
        assertThatCode(() -> provider.validateConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRetrieveMultipleSecrets() {
        Optional<String> result1 = provider.getSecret("test-secret");
        Optional<String> result2 = provider.getSecret("database-password");

        assertThat(result1).isPresent();
        assertThat(result1.get()).isEqualTo("test-value");

        assertThat(result2).isPresent();
        assertThat(result2.get()).isEqualTo("db-pass-123");
    }
}
