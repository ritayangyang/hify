package com.hify.modules.conversation.infra.converter;

import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.infra.entity.ChatMessage;
import com.hify.modules.conversation.infra.entity.ChatSession;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 对话模块 PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatPoConverter {

    ChatSessionDto toSessionDto(ChatSession entity);

    ChatSession toSessionEntity(ChatSessionDto dto);

    ChatMessageDto toMessageDto(ChatMessage entity);

    ChatMessage toMessageEntity(ChatMessageDto dto);
}
