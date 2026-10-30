package com.hify.modules.conversation.api.dto;

import java.time.LocalDateTime;

/**
 * 对话会话 DTO。
 *
 * @param id        会话 id
 * @param agentId   所属 Agent id
 * @param title     会话标题
 * @param status    会话状态（ACTIVE）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ChatSessionDto(
        Long id,
        Long agentId,
        String title,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
