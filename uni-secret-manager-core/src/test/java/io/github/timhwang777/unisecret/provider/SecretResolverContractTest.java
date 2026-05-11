package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.RetryOptions;
import io.github.timhwang777.unisecret.config.SecretCacheOptions;
import io.github.timhwang777.unisecret.config.SecretResolutionOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class SecretResolverContractTest {

    @Test
    void resolvesByProviderChainOrder() {
        FakeProvider aws = new FakeProvider(ProviderId.AWS, true);
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        local.values.put("db", "local-secret");
        SecretResolver resolver = resolver(List.of(aws, local), List.of("aws", "local"));

        assertThat(resolver.resolve("db")).isEqualTo("local-secret");
        assertThat(aws.calls).isEqualTo(1);
        assertThat(local.calls).isEqualTo(1);
    }

    @Test
    void skipsDisabledProviders() {
        FakeProvider aws = new FakeProvider(ProviderId.AWS, false);
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        aws.values.put("db", "aws-secret");
        local.values.put("db", "local-secret");
        SecretResolver resolver = resolver(List.of(aws, local), List.of("aws", "local"));

        assertThat(resolver.resolve("db")).isEqualTo("local-secret");
        assertThat(aws.calls).isEqualTo(0);
        assertThat(local.calls).isEqualTo(1);
    }

    @Test
    void providerSpecificResultsDoNotShareWrongCacheKey() {
        FakeProvider aws = new FakeProvider(ProviderId.AWS, true);
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        aws.values.put("shared", "aws-secret");
        local.values.put("shared", "local-secret");
        SecretResolver resolver = resolver(List.of(aws, local), List.of("aws", "local"));

        assertThat(resolver.resolve(reference("shared", "aws"))).isEqualTo("aws-secret");
        assertThat(resolver.resolve(reference("shared", "local"))).isEqualTo("local-secret");
    }

    @Test
    void defaultValuesAreReturnedButNotCached() {
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        SecretResolver resolver = resolver(List.of(local), List.of("local"));
        SecretReference reference = SecretReference.builder()
                .key("optional")
                .defaultValue("fallback")
                .build();

        assertThat(resolver.resolve(reference)).isEqualTo("fallback");
        local.values.put("optional", "real");
        assertThat(resolver.resolve(reference)).isEqualTo("real");
    }

    @Test
    void zeroProvidersAreAllowedUntilResolutionNeedsAValue() {
        SecretResolver resolver = resolver(List.of(), List.of());

        assertThat(resolver.find("missing")).isEmpty();
        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void retryableProviderErrorsAreRetried() {
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        local.failuresBeforeSuccess = 2;
        local.values.put("db", "secret");
        SecretResolver resolver = resolver(List.of(local), List.of("local"));

        assertThat(resolver.resolve("db")).isEqualTo("secret");
        assertThat(local.calls).isEqualTo(3);
    }

    @Test
    void nonRetryableProviderErrorsAreNotRetried() {
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        local.alwaysNonRetryable = true;
        SecretResolver resolver = resolver(List.of(local), List.of("local"));
        SecretReference reference = SecretReference.builder()
                .key("db")
                .defaultValue("fallback")
                .build();

        assertThat(resolver.resolve(reference)).isEqualTo("fallback");
        assertThat(local.calls).isEqualTo(1);
    }

    @Test
    void findReturnsEmptyForAbsentSecret() {
        FakeProvider local = new FakeProvider(ProviderId.LOCAL, true);
        SecretResolver resolver = resolver(List.of(local), List.of("local"));

        assertThat(resolver.find("missing")).isEmpty();
    }

    @Test
    void customProviderIdsResolveWithoutCoreConstants() {
        FakeProvider custom = new FakeProvider(ProviderId.of("azure"), true);
        custom.values.put("db", "azure-secret");
        SecretResolver resolver = resolver(List.of(custom), List.of("azure"));

        assertThat(resolver.resolve("db")).isEqualTo("azure-secret");
    }

    @Test
    void duplicateProviderIdsAfterNormalizationAreRejected() {
        FakeProvider first = new FakeProvider(ProviderId.of("LOCAL"), true);
        FakeProvider second = new FakeProvider(ProviderId.LOCAL, true);

        assertThatThrownBy(() -> resolver(List.of(first, second), List.of("local")))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Duplicate provider id 'local'");
    }

    @Test
    void referencedButUnregisteredProviderIdsFailClearly() {
        SecretResolver resolver = resolver(List.of(), List.of("azure"));

        assertThatThrownBy(() -> resolver.resolve("db"))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider 'azure' is referenced but not configured");
    }

    @Test
    void defaultProviderOrderIsEmpty() {
        SecretResolutionOptions options = SecretResolutionOptions.defaults();

        assertThat(options.providerOrder()).isEmpty();
    }

    private SecretResolver resolver(List<SecretProvider> providers, List<String> order) {
        RetryOptions retry = new RetryOptions(3, Duration.ZERO, 1.0, Duration.ZERO);
        SecretResolutionOptions resolutionOptions = new SecretResolutionOptions(order, true, retry);
        SecretCache cache = new SecretCache(new SecretCacheOptions(true, Duration.ofMinutes(1), 100));
        return new SecretResolver(providers, resolutionOptions, cache);
    }

    private SecretReference reference(String key, String provider) {
        return SecretReference.builder()
                .key(key)
                .provider(provider)
                .build();
    }

    private static final class FakeProvider implements SecretProvider {

        private final ProviderId providerId;
        private final boolean enabled;
        private final Map<String, String> values = new java.util.HashMap<>();
        private int calls;
        private int failuresBeforeSuccess;
        private boolean alwaysNonRetryable;

        private FakeProvider(ProviderId providerId, boolean enabled) {
            this.providerId = providerId;
            this.enabled = enabled;
        }

        @Override
        public Optional<String> getSecret(String key) {
            return getSecret(key, "latest");
        }

        @Override
        public Optional<String> getSecret(String key, String version) {
            calls++;
            if (alwaysNonRetryable) {
                throw new SecretProviderException("denied", null, false);
            }
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--;
                throw new SecretProviderException("temporary", null, true);
            }
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public ProviderId getProviderId() {
            return providerId;
        }

        @Override
        public void validateConfiguration() {
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
