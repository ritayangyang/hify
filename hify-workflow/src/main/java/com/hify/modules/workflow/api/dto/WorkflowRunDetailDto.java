package com.hify.modules.workflow.api.dto;

import java.util.List;

/**
 * 执行记录详情：整次执行 + 按顺序排列的节点执行记录。
 */
public record WorkflowRunDetailDto(
        WorkflowRunDto run,
        List<WorkflowNodeRunDto> nodeRuns
) {
}
