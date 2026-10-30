package com.hify.modules.conversation.api.service;

import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.api.dto.CreateSessionCmd;
import com.hify.modules.conversation.api.dto.CursorPageDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话服务：会话管理 + 基于 SSE 的流式消息交互。
 */
public interface ChatService {

    /** 创建会话：校验 Agent 存在且启用，标题默认「新会话」 */
    ChatSessionDto createSession(CreateSessionCmd cmd);

    /** 会话游标分页（id 倒序，cursorId 为上一页最末一条 id，首页传 null） */
    CursorPageDto<ChatSessionDto> listSessions(Long cursorId, Integer size);

    /** 逻辑删除会话及其消息 */
    void deleteSession(Long id);

    /** 会话内消息游标分页（id 倒序，即最新消息在前） */
    CursorPageDto<ChatMessageDto> listMessages(Long sessionId, Long cursorId, Integer size);

    /**
     * 发送一条用户消息并返回 SSE 流。
     * 方法本身只负责建 emitter 并把任务提交到流式线程池，立即返回；
     * Agent 绑定工作流时触发工作流（结果整段下发），否则走普通 LLM 流式对话。
     * SSE 事件：delta / done / error，载荷均为 JSON。
     */
    SseEmitter sendMessage(Long sessionId, String content);
}
