package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor;
import io.github.timhwang777.unisecret.provider.SecretReference;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretValueBeanPostProcessorTest {

    @Mock
    private SecretResolver secretResolver;

    @Mock
    private ObjectProvider<SecretResolver> secretResolverProvider;

    private SecretValueBeanPostProcessor processor;

    static class TestBean {
        @SecretValue("test-secret")
        private String secretField;

        @SecretValue("another-secret")
        private String anotherSecretField;

        private String normalField;
    }

    static class InvalidProviderBean {
        @SecretValue(value = "test-secret", provider = "vault")
        private String secretField;
    }

    static class InvalidProvidersBean {
        @SecretValue(value = "test-secret", providers = {"local", "vault"})
        private String secretField;
    }

    @BeforeEach
    void setUp() {
        processor = new SecretValueBeanPostProcessor(secretResolverProvider);
    }

    @Test
    void shouldInjectSecretValues() {
        TestBean bean = new TestBean();

        when(secretResolverProvider.getObject()).thenReturn(secretResolver);
        when(secretResolver.resolve(any(SecretReference.class))).thenReturn("injected-value");

        Object result = processor.postProcessBeforeInitialization(bean, "testBean");

        assertThat(result).isSameAs(bean);
        assertThat(bean.secretField).isEqualTo("injected-value");
        assertThat(bean.anotherSecretField).isEqualTo("injected-value");
        assertThat(bean.normalField).isNull();
        verify(secretResolver, times(2)).resolve(any(SecretReference.class));
    }

    @Test
    void shouldNotModifyFieldsWithoutAnnotation() {
        TestBean bean = new TestBean();
        bean.normalField = "original-value";

        when(secretResolverProvider.getObject()).thenReturn(secretResolver);
        when(secretResolver.resolve(any(SecretReference.class))).thenReturn("injected-value");

        processor.postProcessBeforeInitialization(bean, "testBean");

        assertThat(bean.normalField).isEqualTo("original-value");
    }

    @Test
    void shouldHandleBeanWithNoAnnotatedFields() {
        Object bean = new Object();

        Object result = processor.postProcessBeforeInitialization(bean, "plainBean");

        assertThat(result).isSameAs(bean);
        verify(secretResolver, never()).resolve(any(SecretReference.class));
    }

    @Test
    void shouldFailFastWhenAnnotationProviderIsNotConfigured() {
        InvalidProviderBean bean = new InvalidProviderBean();

        when(secretResolverProvider.getObject()).thenReturn(secretResolver);
        doThrow(new SecretConfigurationException("Provider 'vault' is referenced but not configured"))
                .when(secretResolver).validateConfiguredProviders(java.util.List.of("vault"));

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(bean, "invalidProviderBean"))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider 'vault' is referenced but not configured");

        verify(secretResolver, never()).resolve(any(SecretReference.class));
    }

    @Test
    void shouldFailFastWhenAnnotationProviderChainContainsUnconfiguredProvider() {
        InvalidProvidersBean bean = new InvalidProvidersBean();

        when(secretResolverProvider.getObject()).thenReturn(secretResolver);
        doThrow(new SecretConfigurationException("Provider 'vault' is referenced but not configured"))
                .when(secretResolver).validateConfiguredProviders(java.util.List.of("local", "vault"));

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(bean, "invalidProvidersBean"))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider 'vault' is referenced but not configured");

        verify(secretResolver, never()).resolve(any(SecretReference.class));
    }
}
