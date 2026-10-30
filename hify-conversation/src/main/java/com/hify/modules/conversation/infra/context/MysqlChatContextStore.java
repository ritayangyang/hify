package com.hify.modules.conversation.infra.context;

import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.domain.context.ChatContextStore;
import com.hify.modules.conversation.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 MySQL 的对话上下文装载（本期唯一实现）。
 * 后续接入 Redis 时新增 ChatContextStore 实现即可，对话编排逻辑不变。
 */
@Repository
@RequiredArgsConstructor
public class MysqlChatContextStore implements ChatContextStore {

    private final ChatMessageRepository messageRepository;

    @Override
    public List<ChatMessageDto> loadRecentContext(Long sessionId, int limit) {
        return messageRepository.findRecentBySession(sessionId, limit);
    }
}
