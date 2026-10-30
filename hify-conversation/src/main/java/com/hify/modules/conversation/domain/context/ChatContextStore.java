package com.hify.modules.conversation.domain.context;

import com.hify.modules.conversation.api.dto.ChatMessageDto;

import java.util.List;

/**
 * 对话上下文装载端口。
 * 本期唯一实现走 MySQL；后续接入 Redis 缓存时新增实现即可，对话编排逻辑不变。
 */
public interface ChatContextStore {

    /**
     * 取会话最近 limit 条历史消息（不含尚未落库的当前消息），按 id 倒序返回，
     * 调用方组装模型入参时反转为时间升序。
     */
    List<ChatMessageDto> loadRecentContext(Long sessionId, int limit);
}
