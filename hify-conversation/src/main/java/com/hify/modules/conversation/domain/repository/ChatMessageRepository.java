package com.hify.modules.conversation.domain.repository;

import com.hify.modules.conversation.api.dto.ChatMessageDto;

import java.util.List;

/**
 * 对话消息仓储端口。
 */
public interface ChatMessageRepository {

    ChatMessageDto insert(ChatMessageDto message);

    /**
     * 游标分页：查某会话内 id &lt; cursorId 的消息，id 倒序，取至多 limit 条。
     * cursorId 为 null 时从最新一条开始。
     */
    List<ChatMessageDto> listBySessionCursor(Long sessionId, Long cursorId, int limit);

    /**
     * 装载模型上下文：查某会话最近 limit 条消息，按 id <b>倒序</b>返回（调用方自行反转为升序）。
     */
    List<ChatMessageDto> findRecentBySession(Long sessionId, int limit);

    /** 删除会话时逻辑删除其全部消息 */
    int deleteBySessionId(Long sessionId);
}
