package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

class SecretValueAnnotationTest {

    static class TestBean {
        @SecretValue("test-secret")
        private String simpleSecret;

        @SecretValue(value = "db-secret", defaultValue = "default-value")
        private String secretWithDefault;

        @SecretValue(value = "aws-secret", provider = "aws")
        private String secretWithProvider;

        @SecretValue(value = "multi-secret", providers = {"local", "aws"})
        private String secretWithProviders;

        @SecretValue(value = "json-secret", field = "password")
        private String secretWithField;

        @SecretValue(value = "versioned-secret", version = "v1")
        private String secretWithVersion;
    }

    @Test
    void shouldHaveCorrectDefaultValues() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("simpleSecret");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("test-secret");
        assertThat(annotation.defaultValue()).isEmpty();
        assertThat(annotation.provider()).isEmpty();
        assertThat(annotation.providers()).isEmpty();
        assertThat(annotation.field()).isEmpty();
        assertThat(annotation.version()).isEqualTo("latest");
    }

    @Test
    void shouldSupportDefaultValue() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("secretWithDefault");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation.defaultValue()).isEqualTo("default-value");
    }

    @Test
    void shouldSupportProviderOverride() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("secretWithProvider");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation.provider()).isEqualTo("aws");
    }

    @Test
    void shouldSupportCustomProviderChain() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("secretWithProviders");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation.providers()).containsExactly("local", "aws");
    }

    @Test
    void shouldSupportJsonFieldPath() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("secretWithField");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation.field()).isEqualTo("password");
    }

    @Test
    void shouldSupportVersion() throws NoSuchFieldException {
        Field field = TestBean.class.getDeclaredField("secretWithVersion");
        SecretValue annotation = field.getAnnotation(SecretValue.class);

        assertThat(annotation.version()).isEqualTo("v1");
    }
}
