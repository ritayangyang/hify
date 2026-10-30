package com.hify.modules.workflow.api.dto;

/**
 * 入参中的原始连线。
 */
public record WorkflowEdgeInput(
        String id,
        String source,
        String sourceHandle,
        String target
) {
}
