package com.hify.modules.conversation.domain.repository;

import com.hify.modules.conversation.api.dto.ChatSessionDto;

import java.util.List;

/**
 * 对话会话仓储端口。
 */
public interface ChatSessionRepository {

    ChatSessionDto insert(ChatSessionDto session);

    ChatSessionDto findById(Long id);

    /** 首条消息到达后更新标题 */
    boolean updateTitle(Long id, String title);

    int deleteById(Long id);

    /**
     * 游标分页：查 id &lt; cursorId 的会话，id 倒序，取至多 limit 条。
     * cursorId 为 null 时从最新一条开始。
     */
    List<ChatSessionDto> listByIdCursor(Long cursorId, int limit);
}
