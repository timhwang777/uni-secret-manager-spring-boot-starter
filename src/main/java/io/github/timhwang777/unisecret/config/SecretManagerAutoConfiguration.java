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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Spring Boot Auto-Configuration for the Universal Secret Manager.
 *
 * <h2>What is Auto-Configuration?</h2>
 * Spring Boot auto-configuration automatically creates and wires beans based on:
 * <ul>
 *   <li>Classes available on the classpath (@ConditionalOnClass)</li>
 *   <li>Properties in configuration (@ConditionalOnProperty)</li>
 *   <li>Other conditions like @ConditionalOnMissingBean</li>
 * </ul>
 *
 * <h2>Beans Created</h2>
 * This configuration creates the following beans (conditionally):
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    ALWAYS CREATED                          │
 * ├─────────────────────────────────────────────────────────────┤
 * │ SecretCache                 - Caches resolved secrets      │
 * │ SecretResolver              - Orchestrates provider chain  │
 * │ SecretValueBeanPostProcessor - Processes @SecretValue      │
 * │ SecretRefreshService        - Runtime refresh capability   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │          CONDITIONAL (when secrets.aws.enabled=true)       │
 * ├─────────────────────────────────────────────────────────────┤
 * │ SecretsManagerClient        - AWS SDK client               │
 * │ AwsSecretProvider           - AWS provider implementation  │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │          CONDITIONAL (when secrets.gcp.enabled=true)       │
 * ├─────────────────────────────────────────────────────────────┤
 * │ SecretManagerServiceClient  - GCP SDK client               │
 * │ GcpSecretProvider           - GCP provider implementation  │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │          CONDITIONAL (when secrets.local.enabled=true)     │
 * ├─────────────────────────────────────────────────────────────┤
 * │ LocalSecretProvider         - Local/dev provider           │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>How to Disable</h2>
 * To completely disable the secret manager, set:
 * <pre>{@code
 * secrets:
 *   enabled: false
 * }</pre>
 *
 * @see SecretManagerProperties
 * @see SecretResolver
 */
@Slf4j
@AutoConfiguration  // Marks this as a Spring Boot auto-configuration class
@ConditionalOnProperty(prefix = "secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
// ↑ Only activate if secrets.enabled=true (or not set, since matchIfMissing=true)
@EnableConfigurationProperties(SecretManagerProperties.class)  // Bind secrets.* properties
public class SecretManagerAutoConfiguration {

    // ==================== AWS Provider Beans ====================

    /**
     * Creates the AWS Secrets Manager SDK client.
     *
     * <p>This bean is only created when:</p>
     * <ul>
     *   <li>secrets.aws.enabled=true in configuration</li>
     *   <li>AWS SDK classes are on the classpath</li>
     * </ul>
     *
     * @param properties configuration properties for AWS settings
     * @return configured SecretsManagerClient
     */
    @Bean
    @ConditionalOnProperty(prefix = "secrets.aws", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "software.amazon.awssdk.services.secretsmanager.SecretsManagerClient")
    public SecretsManagerClient secretsManagerClient(SecretManagerProperties properties) {
        log.info("Creating AWS Secrets Manager client");

        var builder = SecretsManagerClient.builder();
        SecretManagerProperties.Aws awsConfig = properties.getAws();

        // Configure region if explicitly specified
        // Otherwise, AWS SDK uses default region chain (env var, profile, EC2 metadata)
        if (awsConfig.getRegion() != null && !awsConfig.getRegion().isEmpty()) {
            builder.region(Region.of(awsConfig.getRegion()));
        }

        // Configure custom endpoint for LocalStack or VPC endpoints
        if (awsConfig.getEndpoint() != null && !awsConfig.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(awsConfig.getEndpoint()));
        }

        return builder.build();
    }

    /**
     * Creates the AWS Secret Provider that wraps the SDK client.
     *
     * <p>Depends on the SecretsManagerClient bean being created first.</p>
     *
     * @param client     the AWS SDK client (auto-wired)
     * @param properties configuration properties
     * @return validated AwsSecretProvider
     */
    @Bean
    @ConditionalOnProperty(prefix = "secrets.aws", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "software.amazon.awssdk.services.secretsmanager.SecretsManagerClient")
    public AwsSecretProvider awsSecretProvider(SecretsManagerClient client, SecretManagerProperties properties) {
        log.info("Creating AWS Secret Provider");
        AwsSecretProvider provider = new AwsSecretProvider(client, properties.getAws());
        provider.validateConfiguration();  // Log configuration at startup
        return provider;
    }

    // ==================== GCP Provider Beans ====================

    /**
     * Creates the GCP Secret Manager SDK client.
     *
     * <p>Uses Application Default Credentials (ADC) for authentication.
     * The client is auto-closed when the Spring context shuts down.</p>
     *
     * @return configured SecretManagerServiceClient
     * @throws IOException if credentials cannot be loaded
     */
    @Bean(destroyMethod = "close")  // Auto-close when Spring shuts down
    @ConditionalOnProperty(prefix = "secrets.gcp", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "com.google.cloud.secretmanager.v1.SecretManagerServiceClient")
    public SecretManagerServiceClient secretManagerServiceClient() throws IOException {
        log.info("Creating GCP Secret Manager client");
        // Uses Application Default Credentials (ADC) automatically
        // ADC checks: GOOGLE_APPLICATION_CREDENTIALS env var, gcloud auth, metadata server
        return SecretManagerServiceClient.create();
    }

    /**
     * Creates the GCP Secret Provider that wraps the SDK client.
     *
     * @param client     the GCP SDK client (auto-wired)
     * @param properties configuration properties
     * @return validated GcpSecretProvider
     */
    @Bean
    @ConditionalOnProperty(prefix = "secrets.gcp", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "com.google.cloud.secretmanager.v1.SecretManagerServiceClient")
    public GcpSecretProvider gcpSecretProvider(SecretManagerServiceClient client, SecretManagerProperties properties) {
        log.info("Creating GCP Secret Provider");
        GcpSecretProvider provider = new GcpSecretProvider(client, properties.getGcp());
        provider.validateConfiguration();  // Warn if project ID not set
        return provider;
    }

    // ==================== Local Provider Bean ====================

    /**
     * Creates the Local Secret Provider for development and testing.
     *
     * <p>Reads secrets from Spring Environment (properties, YAML, env vars).</p>
     *
     * @param environment Spring Environment for property lookup
     * @param properties  configuration properties
     * @return validated LocalSecretProvider
     */
    @Bean
    @ConditionalOnProperty(prefix = "secrets.local", name = "enabled", havingValue = "true")
    public LocalSecretProvider localSecretProvider(Environment environment, SecretManagerProperties properties) {
        log.info("Creating Local Secret Provider");
        LocalSecretProvider provider = new LocalSecretProvider(environment, properties.getLocal());
        provider.validateConfiguration();  // Log number of configured secrets
        return provider;
    }

    // ==================== Core Infrastructure Beans ====================

    /**
     * Creates the secret cache for storing resolved secrets in memory.
     *
     * <p>Always created (caching can be disabled via configuration).</p>
     *
     * @param properties cache configuration (TTL, max size, enabled)
     * @return configured SecretCache
     */
    @Bean
    public SecretCache secretCache(SecretManagerProperties properties) {
        log.info("Creating Secret Cache");
        return new SecretCache(properties.getCache());
    }

    /**
     * Creates the SecretResolver that orchestrates the provider chain.
     *
     * <p>Receives all SecretProvider beans that were conditionally created
     * based on which providers are enabled.</p>
     *
     * @param providers  all enabled SecretProvider beans (auto-wired as list)
     * @param properties global configuration properties
     * @param cache      the secret cache
     * @return configured SecretResolver
     */
    @Bean
    public SecretResolver secretResolver(List<SecretProvider> providers, SecretManagerProperties properties, SecretCache cache) {
        log.info("Creating Secret Resolver");
        return new SecretResolver(providers, properties, cache);
    }

    /**
     * Creates the BeanPostProcessor that handles @SecretValue annotations.
     *
     * <p>This processor runs during Spring bean initialization and injects
     * secret values into annotated fields.</p>
     *
     * <p>This method is {@code static} to avoid early instantiation of the
     * configuration class and its dependencies. An {@link ObjectProvider} is
     * used so that the {@link SecretResolver} (and its transitive dependencies)
     * are resolved lazily — only when the first {@code @SecretValue}-annotated
     * bean is processed — rather than at BeanPostProcessor registration time.</p>
     *
     * @param secretResolverProvider lazy provider for the secret resolver
     * @return configured SecretValueBeanPostProcessor
     */
    @Bean
    public static SecretValueBeanPostProcessor secretValueBeanPostProcessor(ObjectProvider<SecretResolver> secretResolverProvider) {
        Logger logger = LoggerFactory.getLogger(SecretManagerAutoConfiguration.class);
        logger.info("Creating SecretValue BeanPostProcessor");
        return new SecretValueBeanPostProcessor(secretResolverProvider);
    }

    /**
     * Creates the SecretRefreshService for runtime cache invalidation.
     *
     * <p>Allows applications to trigger secret refresh without restart.</p>
     *
     * @param cache      the cache to invalidate
     * @param properties configuration properties
     * @return configured SecretRefreshService
     */
    @Bean
    public SecretRefreshService secretRefreshService(SecretCache cache, SecretManagerProperties properties) {
        log.info("Creating Secret Refresh Service");
        return new SecretRefreshService(cache, properties);
    }
}
