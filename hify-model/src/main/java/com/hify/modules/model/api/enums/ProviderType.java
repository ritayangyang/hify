package com.hify.modules.model.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 提供商类型（协议分发用），数据库存 code（TINYINT）。
 */
@Getter
public enum ProviderType {

    OPENAI(1, "OpenAI"),
    OPENAI_COMPATIBLE(2, "OpenAI 兼容"),
    ANTHROPIC(3, "Anthropic"),
    OLLAMA(4, "Ollama");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    ProviderType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 反序列化兼容两种入参：枚举名 "OPENAI" 和数字编码 "1"/1（Jackson 会把数字标量强转为字符串）。
     */
    @JsonCreator
    public static ProviderType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            int code = Integer.parseInt(trimmed);
            for (ProviderType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的提供商类型编码: " + trimmed);
        }
        try {
            return ProviderType.valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的提供商类型: " + trimmed);
        }
    }
}
