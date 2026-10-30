package com.hify.modules.agent.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 列表行：Agent 字段 + 绑定模型摘要 + 关联资源数（服务端批量装配，无 N+1）。
 */
public record AgentListItemDto(
        Long id,
        String name,
        String description,
        String systemPrompt,
        Long modelConfigId,
        Map<String, Object> modelParams,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 绑定模型展示名；模型被物理删除时为 null */
        String modelName,
        /** 绑定模型所属提供商名称 */
        String providerName,
        /** 关联知识库数 */
        int knowledgeCount,
        /** 关联 MCP 工具数 */
        int mcpCount
) {
}
