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
