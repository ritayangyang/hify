package com.hify.modules.agent.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 创建 Agent 命令。知识库/MCP 资源表尚未落地，本期接收但不校验资源存在性。
 *
 * @param knowledgeIds 关联知识库 id 列表，null 视同空列表（去重后落库）
 * @param mcpToolIds   关联 MCP 工具 id 列表，null 视同空列表（去重后落库）
 * @param workflowId   绑定工作流 id，null 表示普通对话
 */
public record AgentCreateCmd(
        String name,
        String description,
        String systemPrompt,
        Long modelConfigId,
        Map<String, Object> modelParams,
        Integer status,
        List<Long> knowledgeIds,
        List<Long> mcpToolIds,
        Long workflowId
) {
}
