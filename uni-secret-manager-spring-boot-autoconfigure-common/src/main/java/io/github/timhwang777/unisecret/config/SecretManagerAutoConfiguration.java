package io.github.timhwang777.unisecret.config;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor;
import io.github.timhwang777.unisecret.provider.AwsSecretProvider;
import io.github.timhwang777.unisecret.provider.GcpSecretProvider;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.util.List;

/**
 * Spring Boot auto-configuration for the Universal Secret Manager.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecretManagerProperties.class)
public class SecretManagerAutoConfiguration {

    /**
     * Creates the AWS Secrets Manager SDK client.
     *
     * @param properties configuration properties
     * @return configured SecretsManagerClient
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "secrets.aws", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "software.amazon.awssdk.services.secretsmanager.SecretsManagerClient")
    public SecretsManagerClient secretsManagerClient(SecretManagerProperties properties) {
        var options = SecretOptionsMapper.aws(properties.getAws());
        var builder = SecretsManagerClient.builder();

        if (options.region() != null && !options.region().isBlank()) {
            builder.region(Region.of(options.region()));
        }
        if (options.endpoint() != null) {
            builder.endpointOverride(options.endpoint());
        }

        return builder.build();
    }

    /**
     * Creates the AWS provider.
     *
     * @param client AWS SDK client
     * @param properties configuration properties
     * @return configured provider
     */
    @Bean
    @ConditionalOnMissingBean(AwsSecretProvider.class)
    @ConditionalOnProperty(prefix = "secrets.aws", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "software.amazon.awssdk.services.secretsmanager.SecretsManagerClient")
    public AwsSecretProvider awsSecretProvider(
            SecretsManagerClient client,
            SecretManagerProperties properties
    ) {
        AwsSecretProvider provider = new AwsSecretProvider(client, SecretOptionsMapper.aws(properties.getAws()));
        provider.validateConfiguration();
        return provider;
    }

    /**
     * Creates the GCP Secret Manager SDK client.
     *
     * @return configured SecretManagerServiceClient
     * @throws IOException when credentials cannot be loaded
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "secrets.gcp", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "com.google.cloud.secretmanager.v1.SecretManagerServiceClient")
    public SecretManagerServiceClient secretManagerServiceClient() throws IOException {
        return SecretManagerServiceClient.create();
    }

    /**
     * Creates the GCP provider.
     *
     * @param client GCP SDK client
     * @param properties configuration properties
     * @return configured provider
     */
    @Bean
    @ConditionalOnMissingBean(GcpSecretProvider.class)
    @ConditionalOnProperty(prefix = "secrets.gcp", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "com.google.cloud.secretmanager.v1.SecretManagerServiceClient")
    public GcpSecretProvider gcpSecretProvider(
            SecretManagerServiceClient client,
            SecretManagerProperties properties
    ) {
        GcpSecretProvider provider = new GcpSecretProvider(client, SecretOptionsMapper.gcp(properties.getGcp()));
        provider.validateConfiguration();
        return provider;
    }

    /**
     * Creates the local provider.
     *
     * @param properties configuration properties
     * @return configured provider
     */
    @Bean
    @ConditionalOnMissingBean(LocalSecretProvider.class)
    @ConditionalOnProperty(prefix = "secrets.local", name = "enabled", havingValue = "true")
    public LocalSecretProvider localSecretProvider(SecretManagerProperties properties) {
        LocalSecretProvider provider = new LocalSecretProvider(
                SecretOptionsMapper.local(properties.getLocal())
        );
        provider.validateConfiguration();
        return provider;
    }

    /**
     * Creates the secret cache.
     *
     * @param properties configuration properties
     * @return configured cache
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretCache secretCache(SecretManagerProperties properties) {
        return new SecretCache(SecretOptionsMapper.cache(properties.getCache()));
    }

    /**
     * Creates the resolver. It can start with zero providers.
     *
     * @param providers available providers
     * @param properties configuration properties
     * @param cache cache instance
     * @return configured resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretResolver secretResolver(
            List<SecretProvider> providers,
            SecretManagerProperties properties,
            SecretCache cache
    ) {
        return new SecretResolver(providers, SecretOptionsMapper.resolution(properties), cache);
    }

    /**
     * Creates the annotation processor.
     *
     * @param secretResolverProvider lazy resolver provider
     * @return bean post processor
     */
    @Bean
    @ConditionalOnMissingBean
    public static SecretValueBeanPostProcessor secretValueBeanPostProcessor(
            ObjectProvider<SecretResolver> secretResolverProvider
    ) {
        Logger logger = LoggerFactory.getLogger(SecretManagerAutoConfiguration.class);
        logger.info("Creating SecretValue BeanPostProcessor");
        return new SecretValueBeanPostProcessor(secretResolverProvider);
    }

    /**
     * Creates the refresh service.
     *
     * @param cache cache instance
     * @return configured refresh service
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretRefreshService secretRefreshService(SecretCache cache) {
        return new SecretRefreshService(cache);
    }
}
