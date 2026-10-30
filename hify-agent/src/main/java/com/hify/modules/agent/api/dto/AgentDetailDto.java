package com.hify.modules.agent.api.dto;

import java.util.List;

/**
 * Agent 详情：基础信息 + 绑定模型摘要 + 关联资源 id 全量列表。
 *
 * @param agent        Agent 基础字段
 * @param modelName    绑定模型展示名；模型被物理删除时为 null
 * @param providerName 绑定模型所属提供商名称
 * @param knowledgeIds 关联知识库 id 列表（按关联 id 升序）
 * @param mcpToolIds   关联 MCP 工具 id 列表（按关联 id 升序）
 */
public record AgentDetailDto(
        AgentDto agent,
        String modelName,
        String providerName,
        List<Long> knowledgeIds,
        List<Long> mcpToolIds
) {
}
