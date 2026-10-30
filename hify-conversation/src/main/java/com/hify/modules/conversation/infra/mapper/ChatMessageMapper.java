package com.hify.modules.conversation.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.conversation.infra.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
