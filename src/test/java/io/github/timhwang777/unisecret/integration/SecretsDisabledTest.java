package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for verifying that secrets library can be completely disabled.
 */
@SpringBootTest(classes = {
        SecretsDisabledTest.TestConfig.class
})
@TestPropertySource(properties = {
        "secrets.enabled=false"
})
class SecretsDisabledTest {

    @Configuration
    static class TestConfig {
    }

    @Autowired(required = false)
    private SecretResolver resolver;

    @Autowired(required = false)
    private SecretManagerProperties properties;

    @Test
    void shouldNotLoadSecretsLibraryWhenDisabled() {
        assertThat(resolver).isNull();
        assertThat(properties).isNull();
    }
}
