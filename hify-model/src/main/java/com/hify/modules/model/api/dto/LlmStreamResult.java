package com.hify.modules.model.api.dto;

/**
 * 流式对话结束后的汇总结果。
 *
 * @param content          累积的完整回复文本
 * @param promptTokens     输入 token（上游不回 usage 时为 0）
 * @param completionTokens 输出 token（上游不回 usage 时为 0）
 * @param finishReason     stop / length（上游不回时默认 stop）
 * @param latencyMs        首字节到结束的整体耗时
 */
public record LlmStreamResult(
        String content,
        int promptTokens,
        int completionTokens,
        String finishReason,
        long latencyMs
) {
}
