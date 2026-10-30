package com.hify.modules.workflow.api.dto;

import java.util.List;

/**
 * 更新工作流命令：整体替换基本信息与整幅图（节点/连线不做 diff）。
 */
public record WorkflowUpdateCmd(
        String name,
        String description,
        Integer status,
        List<WorkflowNodeInput> nodes,
        List<WorkflowEdgeInput> edges
) {
}
