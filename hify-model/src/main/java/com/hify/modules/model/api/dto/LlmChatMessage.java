package com.hify.modules.model.api.dto;

/**
 * 对话消息（OpenAI 协议风格）。
 *
 * @param role    角色：system / user / assistant
 * @param content 文本内容
 */
public record LlmChatMessage(
        String role,
        String content
) {

    public static LlmChatMessage user(String content) {
        return new LlmChatMessage("user", content);
    }
}
