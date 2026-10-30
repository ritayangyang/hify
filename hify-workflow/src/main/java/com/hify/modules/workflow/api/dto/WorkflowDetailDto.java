package com.hify.modules.workflow.api.dto;

import java.util.List;

/**
 * 工作流详情：基本信息 + 完整节点和连线，由三张表组装。
 */
public record WorkflowDetailDto(
        WorkflowDto workflow,
        List<WorkflowNodeDto> nodes,
        List<WorkflowEdgeDto> edges
) {
}
