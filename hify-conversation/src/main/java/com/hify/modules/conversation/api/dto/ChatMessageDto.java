package com.hify.modules.conversation.api.dto;

import java.time.LocalDateTime;

/**
 * 对话消息 DTO（system 消息只注入模型上下文，不持久化）。
 *
 * @param id           消息 id
 * @param sessionId    所属会话 id
 * @param role         角色：user / assistant
 * @param content      消息文本
 * @param tokens       token 总数（prompt+completion），未知为 null
 * @param finishReason 结束原因：stop / length
 * @param latencyMs    耗时毫秒
 * @param createdAt    创建时间
 */
public record ChatMessageDto(
        Long id,
        Long sessionId,
        String role,
        String content,
        Integer tokens,
        String finishReason,
        Integer latencyMs,
        LocalDateTime createdAt
) {
}
