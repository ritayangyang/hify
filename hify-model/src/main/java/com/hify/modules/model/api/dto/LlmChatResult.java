package com.hify.modules.model.api.dto;

/**
 * 非流式对话调用结果。
 *
 * @param content          模型生成的文本
 * @param promptTokens     输入 token 数（提供商未返回时为 0）
 * @param completionTokens 输出 token 数（提供商未返回时为 0）
 * @param totalTokens      总 token 数（提供商未返回时为 0）
 * @param latencyMs        本次 HTTP 调用耗时毫秒
 */
public record LlmChatResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs
) {
}
