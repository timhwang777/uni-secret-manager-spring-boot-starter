package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.exception.SecretParsingException;
import io.github.timhwang777.unisecret.util.JsonFieldExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonFieldExtractorTest {

    @Test
    void shouldReturnOriginalStringWhenNoFieldPath() {
        String json = "{\"key\":\"value\"}";

        String result = JsonFieldExtractor.extractField(json, null);

        assertThat(result).isEqualTo(json);
    }

    @Test
    void shouldReturnOriginalStringWhenEmptyFieldPath() {
        String json = "{\"key\":\"value\"}";

        String result = JsonFieldExtractor.extractField(json, "");

        assertThat(result).isEqualTo(json);
    }

    @Test
    void shouldExtractSimpleField() {
        String json = "{\"password\":\"secret123\"}";

        String result = JsonFieldExtractor.extractField(json, "password");

        assertThat(result).isEqualTo("secret123");
    }

    @Test
    void shouldExtractNestedField() {
        String json = "{\"database\":{\"password\":\"secret123\"}}";

        String result = JsonFieldExtractor.extractField(json, "database.password");

        assertThat(result).isEqualTo("secret123");
    }

    @Test
    void shouldExtractDeeplyNestedField() {
        String json = "{\"app\":{\"db\":{\"credentials\":{\"password\":\"secret123\"}}}}";

        String result = JsonFieldExtractor.extractField(json, "app.db.credentials.password");

        assertThat(result).isEqualTo("secret123");
    }

    @Test
    void shouldThrowExceptionWhenFieldNotFound() {
        String json = "{\"password\":\"secret123\"}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(json, "username"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("Field 'username' not found");
    }

    @Test
    void shouldThrowExceptionWhenNestedFieldNotFound() {
        String json = "{\"database\":{\"password\":\"secret123\"}}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(json, "database.username"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("Field 'username' not found");
    }

    @Test
    void shouldThrowExceptionWhenJsonInvalid() {
        String invalidJson = "{invalid json}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(invalidJson, "password"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("Failed to parse JSON");
    }

    @Test
    void shouldThrowExceptionWhenFieldIsNull() {
        String json = "{\"password\":null}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(json, "password"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("is null");
    }

    @Test
    void shouldExtractNumericFieldAsString() {
        String json = "{\"port\":5432}";

        String result = JsonFieldExtractor.extractField(json, "port");

        assertThat(result).isEqualTo("5432");
    }

    @Test
    void shouldExtractBooleanFieldAsString() {
        String json = "{\"enabled\":true}";

        String result = JsonFieldExtractor.extractField(json, "enabled");

        assertThat(result).isEqualTo("true");
    }
}
