package com.hify.modules.workflow.api.dto;

import java.time.LocalDateTime;

/**
 * 工作流基础 DTO（不含图）。
 */
public record WorkflowDto(
        Long id,
        String name,
        String description,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
