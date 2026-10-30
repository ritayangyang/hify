package com.hify.modules.conversation.web;

import com.hify.common.web.Result;
import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.api.dto.CreateSessionCmd;
import com.hify.modules.conversation.api.dto.CursorPageDto;
import com.hify.modules.conversation.api.service.ChatService;
import com.hify.modules.conversation.web.req.CreateSessionRequest;
import com.hify.modules.conversation.web.req.SendMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话接口：会话管理 + SSE 消息流。
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 创建会话 */
    @PostMapping("/sessions")
    public Result<ChatSessionDto> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return Result.ok(chatService.createSession(new CreateSessionCmd(request.getAgentId())));
    }

    /** 会话游标列表（id 倒序） */
    @GetMapping("/sessions")
    public Result<CursorPageDto<ChatSessionDto>> sessions(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Integer size) {
        return Result.ok(chatService.listSessions(cursorId, size));
    }

    /** 删除会话（逻辑删除，消息同步逻辑删除） */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return Result.ok();
    }

    /** 会话消息游标历史（id 倒序，最新在前） */
    @GetMapping("/sessions/{id}/messages")
    public Result<CursorPageDto<ChatMessageDto>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Integer size) {
        return Result.ok(chatService.listMessages(id, cursorId, size));
    }

    /**
     * 发送消息，返回 SSE 流：delta / done / error 三类 JSON 事件。
     * 注意：SSE 接口直接返回 SseEmitter，不包 Result。
     */
    @PostMapping(path = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(id, request.getContent());
    }
}
