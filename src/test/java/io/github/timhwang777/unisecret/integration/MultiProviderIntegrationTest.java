package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating multi-provider fallback chain.
 */
@SpringBootTest(classes = {
        MultiProviderIntegrationTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.fail-on-missing=false",
        "secrets.local.enabled=true",
        "secrets.local.secrets.local-secret=local-value",
        "secrets.local.secrets.database-password=local-db-pass",
        "secrets.aws.enabled=false",
        "secrets.gcp.enabled=false"
})
class MultiProviderIntegrationTest {

    @Configuration
    @Import(SecretManagerAutoConfiguration.class)
    static class TestConfig {

        @Bean
        public TestBean testBean() {
            return new TestBean();
        }
    }

    static class TestBean {
        @SecretValue("local-secret")
        private String localSecret;

        @SecretValue("database-password")
        private String databasePassword;

        @SecretValue(value = "missing-secret", defaultValue = "default-value")
        private String missingSecret;

        @SecretValue(value = "json-secret", field = "password", defaultValue = "default-pass")
        private String jsonPassword;

        public String getLocalSecret() {
            return localSecret;
        }

        public String getDatabasePassword() {
            return databasePassword;
        }

        public String getMissingSecret() {
            return missingSecret;
        }

        public String getJsonPassword() {
            return jsonPassword;
        }
    }

    @Autowired(required = false)
    private TestBean testBean;

    @Autowired(required = false)
    private SecretManagerProperties properties;

    @Test
    void shouldInjectSecretsFromLocalProvider() {
        assertThat(testBean).isNotNull();
        assertThat(testBean.getLocalSecret()).isEqualTo("local-value");
        assertThat(testBean.getDatabasePassword()).isEqualTo("local-db-pass");
    }

    @Test
    void shouldUseDefaultValueForMissingSecret() {
        assertThat(testBean).isNotNull();
        assertThat(testBean.getMissingSecret()).isEqualTo("default-value");
    }

    @Test
    void shouldUseDefaultValueForMissingJsonField() {
        assertThat(testBean).isNotNull();
        assertThat(testBean.getJsonPassword()).isEqualTo("default-pass");
    }

    @Test
    void shouldConfigureProviderOrder() {
        assertThat(properties).isNotNull();
        assertThat(properties.getProviderOrder()).containsExactly("local");
    }

    @Test
    void shouldEnableLocalProvider() {
        assertThat(properties).isNotNull();
        assertThat(properties.getLocal().isEnabled()).isTrue();
    }
}
