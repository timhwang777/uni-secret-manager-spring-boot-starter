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
 *
 * <p>All Vault beans are conditional on both:
 * <ul>
 *   <li>{@code secrets.vault.enabled=true} in configuration</li>
 *   <li>{@code VaultTemplate} class on the classpath (spring-vault-core)</li>
 * </ul>
 *
 * <h2>Bean Creation Order</h2>
 * <pre>
 * VaultEndpoint → SslConfiguration → VaultTemplate → VaultSecretProvider
 * </pre>
 *
 * <h2>Authentication Methods</h2>
 * <ul>
 *   <li>TOKEN: uses {@link SimpleSessionManager} (no renewal needed)</li>
 *   <li>APPROLE: uses {@link LifecycleAwareSessionManager} for token renewal</li>
 *   <li>KUBERNETES: uses {@link LifecycleAwareSessionManager} for token renewal</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration(after = SecretManagerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "secrets.vault", name = "enabled", havingValue = "true")
@ConditionalOnClass(VaultTemplate.class)
@EnableConfigurationProperties(SecretManagerProperties.class)
public class VaultAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public VaultEndpoint vaultEndpoint(SecretManagerProperties properties) {
        SecretManagerProperties.Vault vaultProps = properties.getVault();
        VaultEndpoint endpoint = VaultEndpoint.create(vaultProps.getHost(), vaultProps.getPort());
        endpoint.setScheme(vaultProps.getScheme());
        log.info("Vault endpoint: {}://{}:{}", vaultProps.getScheme(), vaultProps.getHost(), vaultProps.getPort());
        return endpoint;
    }

    @Bean
    public SslConfiguration vaultSslConfiguration(SecretManagerProperties properties) {
        String caCertPath = properties.getVault().getSsl().getCaCertPath();
        if (caCertPath != null && !caCertPath.isBlank()) {
            log.info("Vault TLS: using CA cert at {}", caCertPath);
            return SslConfiguration.forTrustStore(new FileSystemResource(caCertPath), null);
        }
        return SslConfiguration.unconfigured();
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint vaultEndpoint,
                                        SslConfiguration vaultSslConfiguration,
                                        SecretManagerProperties properties)
            throws GeneralSecurityException, IOException {
        log.info("Creating Vault template");
        SecretManagerProperties.Vault vaultProps = properties.getVault();
        var requestFactory = ClientHttpRequestFactoryFactory.create(new ClientOptions(), vaultSslConfiguration);
        RestTemplate restTemplate = VaultClients.createRestTemplate(vaultEndpoint, requestFactory);

        ClientAuthentication auth = buildClientAuthentication(vaultProps, restTemplate);
        SessionManager sessionManager = buildSessionManager(vaultProps, auth, restTemplate);

        return new VaultTemplate(vaultEndpoint, requestFactory, sessionManager);
    }

    @Bean
    public VaultSecretProvider vaultSecretProvider(VaultTemplate vaultTemplate,
                                                    SecretManagerProperties properties,
                                                    ObjectMapper objectMapper) {
        log.info("Creating Vault Secret Provider");
        VaultSecretProvider provider = new VaultSecretProvider(
                vaultTemplate, properties.getVault(), objectMapper);
        provider.validateConfiguration();
        return provider;
    }

    private ClientAuthentication buildClientAuthentication(SecretManagerProperties.Vault props,
                                                            RestTemplate restTemplate) {
        switch (props.getAuthMethod()) {
            case TOKEN:
                log.info("Vault auth: TOKEN");
                return new TokenAuthentication(props.getToken());
            case APPROLE:
                log.info("Vault auth: APPROLE (path={})", props.getAppRole().getPath());
                return buildAppRoleAuthentication(props.getAppRole(), restTemplate);
            case KUBERNETES:
                log.info("Vault auth: KUBERNETES (path={})", props.getKubernetes().getPath());
                return buildKubernetesAuthentication(props.getKubernetes(), restTemplate);
            default:
                throw new SecretConfigurationException(
                        "Unsupported Vault auth method: " + props.getAuthMethod());
        }
    }

    private AppRoleAuthentication buildAppRoleAuthentication(
            SecretManagerProperties.Vault.AppRole appRole, RestTemplate restTemplate) {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(appRole.getRoleId()))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(appRole.getSecretId()))
                .path(appRole.getPath())
                .build();
        return new AppRoleAuthentication(options, restTemplate);
    }

    private KubernetesAuthentication buildKubernetesAuthentication(
            SecretManagerProperties.Vault.Kubernetes kubernetes, RestTemplate restTemplate) {
        String tokenPath = kubernetes.getServiceAccountTokenPath();
        KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .role(kubernetes.getRole())
                .jwtSupplier(() -> readServiceAccountToken(tokenPath))
                .path(kubernetes.getPath())
                .build();
        return new KubernetesAuthentication(options, restTemplate);
    }

    private SessionManager buildSessionManager(SecretManagerProperties.Vault props,
                                                ClientAuthentication auth,
                                                RestTemplate restTemplate) {
        if (props.getAuthMethod() == SecretManagerProperties.Vault.AuthMethod.TOKEN) {
            return new SimpleSessionManager(auth);
        }
        log.info("Using LifecycleAwareSessionManager for {} auth renewal", props.getAuthMethod());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setDaemon(true);
        scheduler.initialize();
        return new LifecycleAwareSessionManager(auth, scheduler, restTemplate);
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
