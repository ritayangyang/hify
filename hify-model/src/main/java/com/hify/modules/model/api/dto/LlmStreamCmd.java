package com.hify.modules.model.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 流式对话指令（OpenAI 兼容 SSE）。
 *
 * @param modelConfigId 绑定的对话模型配置 id
 * @param messages      完整消息（system/历史/当前），由调用方组装
 * @param temperature   采样温度，null 用模型默认
 * @param maxTokens     最大生成 token，null 用模型默认
 */
public record LlmStreamCmd(
        Long modelConfigId,
        List<LlmChatMessage> messages,
        BigDecimal temperature,
        Integer maxTokens
) {
}
