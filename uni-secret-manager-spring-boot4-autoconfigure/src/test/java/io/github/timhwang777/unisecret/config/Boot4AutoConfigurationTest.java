package io.github.timhwang777.unisecret.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor;
import io.github.timhwang777.unisecret.provider.AwsSecretProvider;
import io.github.timhwang777.unisecret.provider.GcpSecretProvider;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderId;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import io.github.timhwang777.unisecret.provider.VaultSecretProvider;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.List;
import java.util.Optional;

class Boot4AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecretManagerAutoConfiguration.class,
                    VaultAutoConfiguration.class
            ));

    @Test
    void noProviderEnabledContextStarts() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SecretResolver.class);
            assertThat(context).hasSingleBean(SecretCache.class);
            assertThat(context).doesNotHaveBean(SecretProvider.class);
        });
    }

    @Test
    void localEnabledResolverResolvesLocalSecret() {
        contextRunner
                .withPropertyValues(
                        "secrets.provider-order=local",
                        "secrets.local.enabled=true",
                        "secrets.local.secrets.api-key=local-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalSecretProvider.class);
                    assertThat(context.getBean(SecretResolver.class).resolve("api-key"))
                            .isEqualTo("local-secret");
                });
    }

    @Test
    void awsEnabledUsesUserClientBean() {
        contextRunner
                .withUserConfiguration(UserAwsClientConfiguration.class)
                .withPropertyValues("secrets.aws.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretsManagerClient.class);
                    assertThat(context).hasSingleBean(AwsSecretProvider.class);
                });
    }

    @Test
    void gcpEnabledUsesUserClientBean() {
        contextRunner
                .withUserConfiguration(UserGcpClientConfiguration.class)
                .withPropertyValues(
                        "secrets.gcp.enabled=true",
                        "secrets.gcp.project-id=test-project"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretManagerServiceClient.class);
                    assertThat(context).hasSingleBean(GcpSecretProvider.class);
                });
    }

    @Test
    void vaultDisabledCreatesNoVaultBeans() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(VaultTemplate.class));
    }

    @Test
    void vaultEnabledWithTokenCreatesVaultBeans() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.scheme=http",
                        "secrets.vault.token=test-token"
                )
                .run(context -> assertThat(context).hasSingleBean(VaultTemplate.class));
    }

    @Test
    void vaultEnabledMapsSpringFreeProviderOptionsAndProvider() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=vault.example.com",
                        "secrets.vault.port=8201",
                        "secrets.vault.scheme=http",
                        "secrets.vault.namespace=team-a",
                        "secrets.vault.token=test-token",
                        "secrets.vault.mount=kv",
                        "secrets.vault.kv-version=1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultSecretProviderOptions.class);
                    assertThat(context).hasSingleBean(VaultSecretProvider.class);
                    VaultSecretProviderOptions options = context.getBean(VaultSecretProviderOptions.class);
                    assertThat(options.host()).isEqualTo("vault.example.com");
                    assertThat(options.port()).isEqualTo(8201);
                    assertThat(options.scheme()).isEqualTo("http");
                    assertThat(options.namespace()).isEqualTo("team-a");
                    assertThat(options.mount()).isEqualTo("kv");
                    assertThat(options.kvVersion()).isEqualTo(1);
                    assertThat(options.authMethod()).isEqualTo(VaultSecretProviderOptions.AuthMethod.TOKEN);
                });
    }

    @Test
    void rootDisabledCreatesNoSecretInfrastructureIncludingVault() {
        contextRunner
                .withPropertyValues(
                        "secrets.enabled=false",
                        "secrets.vault.enabled=true",
                        "secrets.vault.token=test-token"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SecretResolver.class);
                    assertThat(context).doesNotHaveBean(SecretCache.class);
                    assertThat(context).doesNotHaveBean(SecretValueBeanPostProcessor.class);
                    assertThat(context).doesNotHaveBean(VaultTemplate.class);
                });
    }

    @Test
    void userDefinedInfrastructureBeansWin() {
        contextRunner
                .withUserConfiguration(UserInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretResolver.class);
                    assertThat(context.getBean(SecretResolver.class))
                            .isSameAs(context.getBean("userSecretResolver"));
                    assertThat(context.getBean(SecretCache.class))
                            .isSameAs(context.getBean("userSecretCache"));
                    assertThat(context.getBean(SecretRefreshService.class))
                            .isSameAs(context.getBean("userSecretRefreshService"));
                    assertThat(context.getBean(SecretValueBeanPostProcessor.class))
                            .isSameAs(context.getBean("userSecretValueBeanPostProcessor"));
                });
    }

    @Test
    void localDirectLookupIsDisabledByDefault() {
        contextRunner
                .withPropertyValues(
                        "secrets.provider-order=local",
                        "secrets.local.enabled=true",
                        "direct-secret=should-not-resolve"
                )
                .run(context -> assertThat(context.getBean(SecretResolver.class).find("direct-secret"))
                        .isEmpty());
    }

    @Test
    void providerOrderDefaultIsEmpty() {
        contextRunner.run(context ->
                assertThat(context.getBean(SecretManagerProperties.class).getProviderOrder()).isEmpty());
    }

    @Test
    void customProviderBeanWithCustomProviderIdCanResolve() {
        contextRunner
                .withUserConfiguration(CustomProviderConfiguration.class)
                .withPropertyValues("secrets.provider-order=custom-provider")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretProvider.class);
                    assertThat(context.getBean(SecretResolver.class).resolve("custom-secret"))
                            .isEqualTo("custom-value");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserAwsClientConfiguration {

        @Bean
        SecretsManagerClient userSecretsManagerClient() {
            return mock(SecretsManagerClient.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserGcpClientConfiguration {

        @Bean
        SecretManagerServiceClient userSecretManagerServiceClient() {
            return mock(SecretManagerServiceClient.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserInfrastructureConfiguration {

        @Bean
        SecretCache userSecretCache() {
            return new SecretCache();
        }

        @Bean
        SecretResolver userSecretResolver(SecretCache userSecretCache) {
            return new SecretResolver(List.of(), SecretResolutionOptions.defaults(), userSecretCache);
        }

        @Bean
        SecretRefreshService userSecretRefreshService(SecretCache userSecretCache) {
            return new SecretRefreshService(userSecretCache);
        }

        @Bean
        SecretValueBeanPostProcessor userSecretValueBeanPostProcessor(
                ObjectProvider<SecretResolver> secretResolverProvider
        ) {
            return new SecretValueBeanPostProcessor(secretResolverProvider);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomProviderConfiguration {

        @Bean
        SecretProvider customProvider() {
            return new SecretProvider() {
                @Override
                public Optional<String> getSecret(String key) {
                    return getSecret(key, "latest");
                }

                @Override
                public Optional<String> getSecret(String key, String version) {
                    if ("custom-secret".equals(key)) {
                        return Optional.of("custom-value");
                    }
                    return Optional.empty();
                }

                @Override
                public ProviderId getProviderId() {
                    return ProviderId.of("custom-provider");
                }

                @Override
                public void validateConfiguration() {
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            };
        }
    }
}
