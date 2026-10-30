package com.hify.modules.agent.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 更新 Agent 命令。编辑表单整体提交：可编辑字段按本对象整体替换，
 * 知识库/MCP 关联按 id 列表整体替换（旧关联逻辑删除后重新插入）。
 * 注意 workflowId 与 modelConfigId 的 null 语义不同：表单始终显式提交 workflowId，
 * null 即解绑工作流（区别于 modelConfigId 的 null=保留旧值）。
 *
 * @param workflowId 绑定工作流 id；null 表示解绑，恢复普通对话
 */
public record AgentUpdateCmd(
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
