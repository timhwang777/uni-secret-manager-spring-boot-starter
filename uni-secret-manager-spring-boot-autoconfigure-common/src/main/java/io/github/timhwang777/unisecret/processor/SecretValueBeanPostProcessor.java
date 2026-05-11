package io.github.timhwang777.unisecret.processor;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.provider.SecretReference;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Spring BeanPostProcessor that automatically injects secret values into fields
 * annotated with @SecretValue.
 *
 * <h2>How It Works</h2>
 * This processor is invoked by Spring during bean creation, before the bean's
 * init methods are called. For each bean:
 * <ol>
 *   <li>Scans all declared fields for @SecretValue annotation</li>
 *   <li>For each annotated field, builds a SecretReference from annotation attributes</li>
 *   <li>Uses SecretResolver to fetch the secret value from configured providers</li>
 *   <li>Injects the value into the field using reflection</li>
 * </ol>
 *
 * <h2>Bean Lifecycle</h2>
 * <pre>
 * Bean Creation Timeline:
 * ─────────────────────────────────────────────────────────
 * 1. Constructor called
 * 2. Dependencies injected (@Autowired fields)
 * 3. ▶ postProcessBeforeInitialization ◀ (THIS PROCESSOR)
 *    └── @SecretValue fields injected here
 * 4. @PostConstruct methods called
 * 5. InitializingBean.afterPropertiesSet() called
 * 6. postProcessAfterInitialization
 * 7. Bean is ready for use
 * </pre>
 *
 * <h2>Why Before Initialization?</h2>
 * Secrets are injected in {@code postProcessBeforeInitialization} so that:
 * <ul>
 *   <li>@PostConstruct methods can use the injected secrets</li>
 *   <li>InitializingBean.afterPropertiesSet() has access to secrets</li>
 *   <li>The bean is fully configured before it starts operating</li>
 * </ul>
 *
 * <h2>Security Note</h2>
 * This processor logs the secret key and provider chain but never logs the actual
 * secret value. This is intentional to prevent secrets from appearing in log files.
 *
 * @see SecretValue
 * @see SecretResolver
 * @see SecretReference
 */
@Slf4j
public class SecretValueBeanPostProcessor implements BeanPostProcessor {

    /**
     * Lazy provider for the secret resolver, deferred until the first
     * {@code @SecretValue}-annotated bean is processed. This avoids eager
     * instantiation of the entire provider chain during BeanPostProcessor
     * registration.
     */
    private final ObjectProvider<SecretResolver> secretResolverProvider;

    /**
     * Cached resolver instance, populated on first use.
     */
    private SecretResolver secretResolver;

    public SecretValueBeanPostProcessor(ObjectProvider<SecretResolver> secretResolverProvider) {
        this.secretResolverProvider = secretResolverProvider;
    }

    private SecretResolver getSecretResolver() {
        if (this.secretResolver == null) {
            this.secretResolver = this.secretResolverProvider.getObject();
        }
        return this.secretResolver;
    }

    /**
     * Processes a bean before its initialization callbacks.
     *
     * <p>This method is called by Spring for every bean in the context. It scans
     * each bean's fields for @SecretValue annotations and injects the resolved
     * secret values.</p>
     *
     * @param bean     the bean instance to process
     * @param beanName the name of the bean in the Spring context
     * @return the bean (unchanged - we modify fields in place)
     * @throws BeansException if secret resolution fails
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Use Spring's ReflectionUtils to scan all fields of the bean
        // The filter (second lambda) only processes fields with @SecretValue
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            // Get the annotation (already filtered, but double-check)
            SecretValue annotation = field.getAnnotation(SecretValue.class);
            if (annotation == null) {
                return;
            }

            log.debug("Processing @SecretValue annotation on field '{}' in bean '{}'",
                    field.getName(), beanName);

            // STEP 1: Convert annotation attributes to a SecretReference object
            SecretReference reference = buildReference(annotation);

            // Fail fast during startup if the annotation references providers that are not configured.
            validateProviders(reference);

            // STEP 2: Resolve the secret value using the configured provider chain
            // This may throw SecretNotFoundException if not found and no default
            String secretValue = getSecretResolver().resolve(reference);

            // STEP 3: Inject the value into the field using reflection
            // makeAccessible() handles private fields
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, secretValue);

            // STEP 4: Log the injection for audit purposes
            // SECURITY: We log the key and provider, but NEVER the actual secret value
            log.info("Injected secret '{}' into field '{}' of bean '{}' (provider chain: {})",
                    annotation.value(),
                    field.getName(),
                    beanName,
                    getSecretResolver().getProviderChain(reference));

        }, field -> field.isAnnotationPresent(SecretValue.class));  // Filter: only @SecretValue fields

        return bean;
    }

    /**
     * Converts a @SecretValue annotation into a SecretReference object.
     *
     * <p>This method handles the translation of annotation attributes to the
     * domain model used by SecretResolver:</p>
     * <ul>
     *   <li>Empty strings become null (for proper Optional handling)</li>
     *   <li>Empty arrays become null (for provider chain logic)</li>
     * </ul>
     *
     * @param annotation the @SecretValue annotation from a field
     * @return a SecretReference containing all the annotation's configuration
     */
    private SecretReference buildReference(SecretValue annotation) {
        // Convert empty providers array to null for proper handling
        List<String> providersList = annotation.providers().length > 0
                ? Arrays.asList(annotation.providers())
                : List.of();

        // Build the reference using the builder pattern
        // Empty strings are converted to null for cleaner handling downstream
        return SecretReference.builder()
                .key(annotation.value())                                                    // Required: secret key
                .version(annotation.version())                                              // Defaults to "latest"
                .field(annotation.field().isEmpty() ? null : annotation.field())            // Optional: JSON field path
                .provider(annotation.provider().isEmpty() ? null : annotation.provider())   // Optional: single provider
                .providers(providersList.isEmpty() ? null : providersList)                  // Optional: custom chain
                .defaultValue(annotation.defaultValue().isEmpty() ? null : annotation.defaultValue()) // Optional: fallback
                .build();
    }

    private void validateProviders(SecretReference reference) {
        SecretResolver resolver = getSecretResolver();

        if (reference.getProvider() != null && !reference.getProvider().isBlank()) {
            resolver.validateConfiguredProviders(List.of(reference.getProvider()));
            return;
        }

        if (reference.getProviders() != null && !reference.getProviders().isEmpty()) {
            resolver.validateConfiguredProviders(reference.getProviders());
        }
    }
}
