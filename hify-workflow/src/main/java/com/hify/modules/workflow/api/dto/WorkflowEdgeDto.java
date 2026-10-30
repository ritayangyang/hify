package com.hify.modules.workflow.api.dto;

/**
 * 工作流连线。
 *
 * @param id           图内连线业务 id（前端生成，可能为空）
 * @param source       起点节点业务 id
 * @param sourceHandle 条件分支 id（b1/default）；普通节点唯一出线为 null
 * @param target       终点节点业务 id
 */
public record WorkflowEdgeDto(
        String id,
        String source,
        String sourceHandle,
        String target
) {
}
