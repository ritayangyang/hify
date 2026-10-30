package com.hify.modules.workflow.api.dto;

import java.time.LocalDateTime;

/**
 * 工作流列表行：基本信息 + 节点/连线数量（批量装配，非 N+1）。
 */
public record WorkflowListItemDto(
        Long id,
        String name,
        String description,
        Integer status,
        int nodeCount,
        int edgeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
