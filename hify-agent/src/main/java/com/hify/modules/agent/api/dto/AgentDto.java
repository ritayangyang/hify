package com.hify.modules.agent.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 基础 DTO。
 *
 * @param workflowId 绑定工作流 id，null 表示普通对话
 */
public record AgentDto(
        Long id,
        String name,
        String description,
        String systemPrompt,
        Long modelConfigId,
        Map<String, Object> modelParams,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long workflowId
) {
}
