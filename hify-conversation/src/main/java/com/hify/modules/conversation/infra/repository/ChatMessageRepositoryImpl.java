package com.hify.modules.conversation.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.domain.repository.ChatMessageRepository;
import com.hify.modules.conversation.infra.converter.ChatPoConverter;
import com.hify.modules.conversation.infra.entity.ChatMessage;
import com.hify.modules.conversation.infra.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话消息仓储实现：PO ↔ DTO 转换收口在 infra 层。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper messageMapper;
    private final ChatPoConverter converter;

    @Override
    public ChatMessageDto insert(ChatMessageDto message) {
        ChatMessage entity = converter.toMessageEntity(message);
        messageMapper.insert(entity);
        return converter.toMessageDto(entity);
    }

    @Override
    public List<ChatMessageDto> listBySessionCursor(Long sessionId, Long cursorId, int limit) {
        // idx_session(session_id, deleted, id) 服务：等值 + 游标 + id 倒序，禁止 offset 深分页
        return messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .lt(cursorId != null, ChatMessage::getId, cursorId)
                                .orderByDesc(ChatMessage::getId)
                                .last("LIMIT " + limit))
                .stream()
                .map(converter::toMessageDto)
                .toList();
    }

    @Override
    public List<ChatMessageDto> findRecentBySession(Long sessionId, int limit) {
        // 先 DESC 取最近 N 条，调用方组装模型入参时反转为时间升序（避免 ASC+LIMIT 取到最旧消息）
        return messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByDesc(ChatMessage::getId)
                                .last("LIMIT " + limit))
                .stream()
                .map(converter::toMessageDto)
                .toList();
    }

    @Override
    public int deleteBySessionId(Long sessionId) {
        return messageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId));
    }
}
