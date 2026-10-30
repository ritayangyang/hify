package com.hify.modules.conversation.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.conversation.infra.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
