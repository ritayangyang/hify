package com.hify.modules.workflow.api.dto;

/**
 * 工作流分页查询条件。
 */
public record WorkflowPageQuery(
        long page,
        long size,
        String name,
        Integer status
) {
}
