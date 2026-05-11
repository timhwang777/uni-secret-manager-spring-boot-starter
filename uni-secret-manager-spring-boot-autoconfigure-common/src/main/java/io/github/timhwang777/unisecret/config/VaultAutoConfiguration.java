package io.github.timhwang777.unisecret.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.provider.VaultSecretProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

/**
 * Auto-configuration for the HashiCorp Vault secret provider.
 */
@Slf4j
@AutoConfiguration(after = SecretManagerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "secrets.vault", name = "enabled", havingValue = "true")
@ConditionalOnClass(VaultTemplate.class)
@EnableConfigurationProperties(SecretManagerProperties.class)
public class VaultAutoConfiguration {

    /**
     * Creates the mapper used by Vault provider serialization.
     *
     * @return object mapper
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Creates Spring-free Vault provider options.
     *
     * @param properties configuration properties
     * @return provider options
     */
    @Bean
    @ConditionalOnMissingBean
    public VaultSecretProviderOptions vaultSecretProviderOptions(SecretManagerProperties properties) {
        return SecretOptionsMapper.vault(properties.getVault());
    }

    /**
     * Creates the Vault endpoint.
     *
     * @param options provider options
     * @return endpoint
     */
    @Bean
    @ConditionalOnMissingBean
    VaultEndpoint vaultEndpoint(VaultSecretProviderOptions options) {
        VaultEndpoint endpoint = VaultEndpoint.create(options.host(), options.port());
        endpoint.setScheme(options.scheme());
        log.info("Vault endpoint: {}://{}:{}", options.scheme(), options.host(), options.port());
        return endpoint;
    }

    /**
     * Creates SSL configuration for Vault.
     *
     * @param options provider options
     * @return SSL configuration
     */
    @Bean
    @ConditionalOnMissingBean
    SslConfiguration vaultSslConfiguration(VaultSecretProviderOptions options) {
        String caCertPath = options.ssl().caCertPath();
        if (caCertPath != null && !caCertPath.isBlank()) {
            log.info("Vault TLS: using CA cert at {}", caCertPath);
            return SslConfiguration.forTrustStore(new FileSystemResource(caCertPath), null);
        }
        return SslConfiguration.unconfigured();
    }

    /**
     * Creates the HTTP request factory.
     *
     * @param vaultSslConfiguration SSL configuration
     * @return request factory
     * @throws GeneralSecurityException if TLS setup fails
     * @throws IOException if TLS setup fails
     */
    @Bean
    @ConditionalOnMissingBean
    ClientHttpRequestFactory vaultClientHttpRequestFactory(
            SslConfiguration vaultSslConfiguration
    ) throws GeneralSecurityException, IOException {
        return ClientHttpRequestFactoryFactory.create(new ClientOptions(), vaultSslConfiguration);
    }

    /**
     * Creates the RestTemplate used for authentication.
     *
     * @param vaultEndpoint endpoint
     * @param requestFactory request factory
     * @param options provider options
     * @return rest template
     */
    @Bean
    @ConditionalOnMissingBean(name = "vaultRestTemplate")
    RestTemplate vaultRestTemplate(
            VaultEndpoint vaultEndpoint,
            ClientHttpRequestFactory requestFactory,
            VaultSecretProviderOptions options
    ) {
        RestTemplate restTemplate = VaultClients.createRestTemplate(vaultEndpoint, requestFactory);
        String namespace = options.namespace();
        if (namespace != null && !namespace.isBlank()) {
            restTemplate.getInterceptors().add(VaultClients.createNamespaceInterceptor(namespace));
        }
        return restTemplate;
    }

    /**
     * Creates client authentication.
     *
     * @param options provider options
     * @param vaultRestTemplate rest template
     * @return client authentication
     */
    @Bean
    @ConditionalOnMissingBean
    ClientAuthentication vaultClientAuthentication(
            VaultSecretProviderOptions options,
            RestTemplate vaultRestTemplate
    ) {
        validateAuthConfiguration(options);
        return buildClientAuthentication(options, vaultRestTemplate);
    }

    /**
     * Creates the scheduler used by renewable Vault sessions.
     *
     * @return scheduler
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "vaultSessionTaskScheduler")
    ThreadPoolTaskScheduler vaultSessionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setDaemon(true);
        scheduler.setThreadNamePrefix("unisecret-vault-session-");
        return scheduler;
    }

    /**
     * Creates the Vault session manager.
     *
     * @param options provider options
     * @param clientAuthentication client authentication
     * @param vaultRestTemplate rest template
     * @param vaultSessionTaskScheduler scheduler
     * @return session manager
     */
    @Bean
    @ConditionalOnMissingBean
    SessionManager vaultSessionManager(
            VaultSecretProviderOptions options,
            ClientAuthentication clientAuthentication,
            RestTemplate vaultRestTemplate,
            ThreadPoolTaskScheduler vaultSessionTaskScheduler
    ) {
        if (options.authMethod() == VaultSecretProviderOptions.AuthMethod.TOKEN) {
            return new SimpleSessionManager(clientAuthentication);
        }
        return new LifecycleAwareSessionManager(
                clientAuthentication,
                vaultSessionTaskScheduler,
                vaultRestTemplate
        );
    }

    /**
     * Creates the Vault template.
     *
     * @param vaultEndpoint endpoint
     * @param requestFactory request factory
     * @param vaultSessionManager session manager
     * @return vault template
     */
    @Bean
    @ConditionalOnMissingBean
    VaultTemplate vaultTemplate(
            VaultEndpoint vaultEndpoint,
            ClientHttpRequestFactory requestFactory,
            SessionManager vaultSessionManager
    ) {
        return new VaultTemplate(vaultEndpoint, requestFactory, vaultSessionManager);
    }

    /**
     * Creates the Vault provider.
     *
     * @param vaultTemplate vault template
     * @param options provider options
     * @param objectMapper object mapper
     * @return provider
     */
    @Bean
    @ConditionalOnMissingBean(VaultSecretProvider.class)
    VaultSecretProvider vaultSecretProvider(
            VaultTemplate vaultTemplate,
            VaultSecretProviderOptions options,
            ObjectMapper objectMapper
    ) {
        VaultSecretProvider provider = VaultSecretProvider.springVault(
                vaultTemplate,
                options,
                objectMapper
        );
        provider.validateConfiguration();
        return provider;
    }

    private ClientAuthentication buildClientAuthentication(
            VaultSecretProviderOptions options,
            RestTemplate restTemplate
    ) {
        switch (options.authMethod()) {
            case TOKEN:
                log.info("Vault auth: TOKEN");
                return new TokenAuthentication(options.token());
            case APPROLE:
                log.info("Vault auth: APPROLE (path={})", options.appRole().path());
                return buildAppRoleAuthentication(options.appRole(), restTemplate);
            case KUBERNETES:
                log.info("Vault auth: KUBERNETES (path={})", options.kubernetes().path());
                return buildKubernetesAuthentication(options.kubernetes(), restTemplate);
            default:
                throw new SecretConfigurationException(
                        "Unsupported Vault auth method: " + options.authMethod());
        }
    }

    private void validateAuthConfiguration(VaultSecretProviderOptions options) {
        switch (options.authMethod()) {
            case TOKEN:
                if (options.token() == null || options.token().isBlank()) {
                    throw new SecretConfigurationException(
                            "Vault token must not be blank for TOKEN auth");
                }
                break;
            case APPROLE:
                validateAppRoleConfiguration(options.appRole());
                break;
            case KUBERNETES:
                validateKubernetesConfiguration(options.kubernetes());
                break;
            default:
                throw new SecretConfigurationException(
                        "Unsupported Vault auth method: " + options.authMethod());
        }
    }

    private void validateAppRoleConfiguration(VaultSecretProviderOptions.AppRole appRole) {
        if (appRole.roleId() == null || appRole.roleId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.role-id must not be blank for APPROLE auth");
        }
        if (appRole.secretId() == null || appRole.secretId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.secret-id must not be blank for APPROLE auth");
        }
    }

    private void validateKubernetesConfiguration(VaultSecretProviderOptions.Kubernetes kubernetes) {
        if (kubernetes.role() == null || kubernetes.role().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault kubernetes.role must not be blank for KUBERNETES auth");
        }
    }

    private AppRoleAuthentication buildAppRoleAuthentication(
            VaultSecretProviderOptions.AppRole appRole,
            RestTemplate restTemplate
    ) {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(appRole.roleId()))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(appRole.secretId()))
                .path(appRole.path())
                .build();
        return new AppRoleAuthentication(options, restTemplate);
    }

    private KubernetesAuthentication buildKubernetesAuthentication(
            VaultSecretProviderOptions.Kubernetes kubernetes,
            RestTemplate restTemplate
    ) {
        String tokenPath = kubernetes.serviceAccountTokenPath();
        KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .role(kubernetes.role())
                .jwtSupplier(() -> readServiceAccountToken(tokenPath))
                .path(kubernetes.path())
                .build();
        return new KubernetesAuthentication(options, restTemplate);
    }

    private String readServiceAccountToken(String tokenPath) {
        try {
            return new String(Files.readAllBytes(Paths.get(tokenPath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SecretConfigurationException(
                    "Cannot read Kubernetes service account token: " + tokenPath, e);
        }
    }
}
