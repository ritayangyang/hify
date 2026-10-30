package com.hify.modules.agent.web.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 Agent 请求。
 */
@Data
public class AgentCreateRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    /** 简介 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    @NotNull(message = "modelConfigId 不能为空")
    private Long modelConfigId;

    /** 生成参数覆盖：temperature/top_p/max_tokens */
    private Map<String, Object> modelParams;

    /** 状态：0 禁用，1 启用；为空默认启用 */
    private Integer status;

    /** 关联知识库 id 列表 */
    private List<Long> knowledgeIds;

    /** 关联 MCP 工具 id 列表 */
    private List<Long> mcpToolIds;

    /** 绑定工作流 id；不传或 null 表示普通对话 */
    private Long workflowId;
}
