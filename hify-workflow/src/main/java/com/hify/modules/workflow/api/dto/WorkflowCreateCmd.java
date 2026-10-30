package com.hify.modules.workflow.api.dto;

import java.util.List;

/**
 * 创建工作流命令：完整请求体（基本信息 + 完整节点和连线）。
 */
public record WorkflowCreateCmd(
        String name,
        String description,
        Integer status,
        List<WorkflowNodeInput> nodes,
        List<WorkflowEdgeInput> edges
) {
}
