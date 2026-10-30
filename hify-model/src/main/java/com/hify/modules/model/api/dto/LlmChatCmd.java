package com.hify.modules.model.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 非流式对话调用命令。
 *
 * @param modelConfigId 模型配置 t_model_config.id
 * @param messages      消息列表（按顺序发送）
 * @param temperature   采样温度，null 用模型/提供商默认值
 * @param maxTokens     最大生成 token 数，null 用默认值
 */
public record LlmChatCmd(
        Long modelConfigId,
        List<LlmChatMessage> messages,
        BigDecimal temperature,
        Integer maxTokens
) {
}
