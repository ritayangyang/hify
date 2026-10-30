package com.hify.modules.agent.web.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新 Agent 请求。编辑表单整体提交，可编辑字段与关联按请求整体替换。
 */
@Data
public class AgentUpdateRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    private String description;

    private String systemPrompt;

    @NotNull(message = "modelConfigId 不能为空")
    private Long modelConfigId;

    private Map<String, Object> modelParams;

    private Integer status;

    private List<Long> knowledgeIds;

    private List<Long> mcpToolIds;

    /** 绑定工作流 id；表单始终显式提交，null 即解绑为普通对话 */
    private Long workflowId;
}
