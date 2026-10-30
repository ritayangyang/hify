package com.hify.modules.model.api.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 入参枚举同时接受枚举名（"OPENAI"）与数字编码（"1"），出参仍为数字。
 */
class ProviderTypeTest {

    @Test
    void parsesEnumName() {
        assertThat(ProviderType.of("OPENAI")).isEqualTo(ProviderType.OPENAI);
        assertThat(ProviderType.of("ollama")).isEqualTo(ProviderType.OLLAMA);
    }

    @Test
    void parsesNumericCode() {
        assertThat(ProviderType.of("1")).isEqualTo(ProviderType.OPENAI);
        assertThat(ProviderType.of("4")).isEqualTo(ProviderType.OLLAMA);
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> ProviderType.of("9"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderType.of("GPT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsNullForBlank() {
        assertThat(ProviderType.of(null)).isNull();
        assertThat(ProviderType.of("  ")).isNull();
    }
}
