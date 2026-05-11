package io.github.timhwang777.unisecret.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.timhwang777.unisecret.exception.SecretParsingException;
import org.junit.jupiter.api.Test;

class JsonFieldExtractorContractTest {

    @Test
    void dotPathsTraverseNestedJsonObjects() {
        String json = "{\"database\":{\"password\":\"secret\"}}";

        assertThat(JsonFieldExtractor.extractField(json, "database.password")).isEqualTo("secret");
    }

    @Test
    void missingFieldsFailClearly() {
        String json = "{\"database\":{}}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(json, "database.password"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void nullFieldsFailClearly() {
        String json = "{\"database\":{\"password\":null}}";

        assertThatThrownBy(() -> JsonFieldExtractor.extractField(json, "database.password"))
                .isInstanceOf(SecretParsingException.class)
                .hasMessageContaining("is null");
    }
}
