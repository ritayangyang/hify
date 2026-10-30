package com.hify.modules.conversation.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.domain.repository.ChatSessionRepository;
import com.hify.modules.conversation.infra.converter.ChatPoConverter;
import com.hify.modules.conversation.infra.entity.ChatSession;
import com.hify.modules.conversation.infra.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话会话仓储实现：PO ↔ DTO 转换收口在 infra 层。
 */
@Repository
@RequiredArgsConstructor
public class ChatSessionRepositoryImpl implements ChatSessionRepository {

    private final ChatSessionMapper sessionMapper;
    private final ChatPoConverter converter;

    @Override
    public ChatSessionDto insert(ChatSessionDto session) {
        ChatSession entity = converter.toSessionEntity(session);
        sessionMapper.insert(entity);
        return converter.toSessionDto(entity);
    }

    @Override
    public ChatSessionDto findById(Long id) {
        ChatSession entity = sessionMapper.selectById(id);
        return entity == null ? null : converter.toSessionDto(entity);
    }

    @Override
    public boolean updateTitle(Long id, String title) {
        // title 是普通 varchar，可直接走 wrapper set；updated_at 由 MetaObjectHandler 自动填充
        return sessionMapper.update(null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, id)
                        .set(ChatSession::getTitle, title)) > 0;
    }

    @Override
    public int deleteById(Long id) {
        return sessionMapper.deleteById(id);
    }

    @Override
    public List<ChatSessionDto> listByIdCursor(Long cursorId, int limit) {
        // id 倒序游标：id < cursorId，禁止 LIMIT offset 深分页；limit 为内部归一化后的 int
        return sessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .lt(cursorId != null, ChatSession::getId, cursorId)
                                .orderByDesc(ChatSession::getId)
                                .last("LIMIT " + limit))
                .stream()
                .map(converter::toSessionDto)
                .toList();
    }
}
