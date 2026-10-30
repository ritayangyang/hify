package com.hify.modules.conversation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.api.dto.AgentDetailDto;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.api.service.AgentService;
import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.api.dto.CursorPageDto;
import com.hify.modules.conversation.domain.context.ChatContextStore;
import com.hify.modules.conversation.domain.repository.ChatMessageRepository;
import com.hify.modules.conversation.domain.repository.ChatSessionRepository;
import com.hify.modules.model.api.dto.LlmChatMessage;
import com.hify.modules.model.api.dto.LlmStreamCmd;
import com.hify.modules.model.api.dto.LlmStreamResult;
import com.hify.modules.model.api.service.LlmStreamService;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunStartCmd;
import com.hify.modules.workflow.api.enums.RunStatus;
import com.hify.modules.workflow.api.service.WorkflowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 对话编排单测：SSE 事件通过 ReflectionTestUtils 读取 SseEmitter 建立连接前缓存的
 * earlySendAttempts（每条即我们下发的 JSON 字符串），无需 Servlet 容器。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatContextStore contextStore;
    @Mock
    private AgentService agentService;
    @Mock
    private LlmStreamService llmStreamService;
    @Mock
    private WorkflowRunService workflowRunService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 直接在调用线程执行 worker，保证 sendMessage 返回时流已处理完 */
    private ThreadPoolExecutor sameThreadExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private ChatServiceImpl newService(ThreadPoolExecutor executor) {
        return new ChatServiceImpl(sessionRepository, messageRepository, contextStore,
                agentService, llmStreamService, workflowRunService, executor, objectMapper);
    }

    private ChatSessionDto session(long id, long agentId) {
        return new ChatSessionDto(id, agentId, "新会话", "ACTIVE",
                LocalDateTime.of(2026, 10, 14, 10, 0), null);
    }

    private AgentDto agent(long id, Long workflowId) {
        return new AgentDto(id, "客服 Agent", "简介", "你是客服", 7L,
                Map.of("temperature", 0.7), 1,
                LocalDateTime.of(2026, 10, 13, 10, 0), null, workflowId);
    }

    private ChatMessageDto message(long id, String role, String content) {
        return new ChatMessageDto(id, 10L, role, content, null, null, null,
                LocalDateTime.of(2026, 10, 14, 10, 0).plusSeconds(id));
    }

    /** 读取 emitter 已发送的事件 JSON（建连前发送会被 Spring 缓存） */
    @SuppressWarnings("unchecked")
    private List<JsonNode> sentEvents(SseEmitter emitter) throws Exception {
        Object attempts = ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        List<JsonNode> events = new ArrayList<>();
        for (Object attempt : (Collection<Object>) attempts) {
            // Spring 6.2 的 builder 每个事件会伴随空 SSE 帧（""/"data:"），只保留有载荷的帧，
            // 再剥掉 "data:" 前缀
            String framed = ((String) ReflectionTestUtils.getField(attempt, "data")).strip();
            String json = framed.startsWith("data:")
                    ? framed.substring("data:".length()).strip()
                    : framed;
            if (json.isEmpty()) {
                continue;
            }
            events.add(objectMapper.readTree(json));
        }
        return events;
    }

    private List<String> contentsOf(List<JsonNode> events, String type) {
        return events.stream()
                .filter(e -> type.equals(e.path("type").asText()))
                .map(e -> e.path("content").asText())
                .toList();
    }

    @BeforeEach
    void setUpSessionAndAgent() {
        // 部分用例（线程池拒绝、404、分页）不会走到这两个桩，故声明为 lenient
        lenient().when(sessionRepository.findById(10L)).thenReturn(session(10L, 1L));
        lenient().when(agentService.getDetail(1L)).thenReturn(
                new AgentDetailDto(agent(1L, null), "豆包", "火山方舟", List.of(), List.of()));
    }

    @Test
    void workflowBranchEmitsDeltaDoneAndPersistsAssistantWithRunStats() throws Exception {
        when(agentService.getDetail(1L)).thenReturn(
                new AgentDetailDto(agent(1L, 5L), "豆包", "火山方舟", List.of(), List.of()));
        when(contextStore.loadRecentContext(10L, 20)).thenReturn(List.of());
        WorkflowRunDto run = new WorkflowRunDto(100L, 5L, RunStatus.SUCCEEDED,
                Map.of("userMessage", "退货"), "你好，售后客服为您服务", null,
                1200, 42, LocalDateTime.now(), LocalDateTime.now());
        when(workflowRunService.start(eq(5L), any())).thenReturn(new WorkflowRunDetailDto(run, List.of()));

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(10L, "退货");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(contentsOf(events, "delta")).containsExactly("你好，售后客服为您服务");
        JsonNode done = events.get(events.size() - 1);
        assertThat(done.path("type").asText()).isEqualTo("done");
        assertThat(done.path("finishReason").asText()).isEqualTo("stop");
        assertThat(done.path("latencyMs").asLong()).isGreaterThanOrEqualTo(0);

        // 入参固定映射 userMessage
        ArgumentCaptor<WorkflowRunStartCmd> cmdCaptor = ArgumentCaptor.forClass(WorkflowRunStartCmd.class);
        verify(workflowRunService).start(eq(5L), cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().inputs().get("userMessage")).isEqualTo("退货");

        // user + assistant 两条落库，assistant 带工作流 token/耗时
        ArgumentCaptor<ChatMessageDto> msgCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messageRepository, times(2)).insert(msgCaptor.capture());
        ChatMessageDto assistant = msgCaptor.getAllValues().get(1);
        assertThat(assistant.role()).isEqualTo("assistant");
        assertThat(assistant.content()).isEqualTo("你好，售后客服为您服务");
        assertThat(assistant.tokens()).isEqualTo(42);
        assertThat(assistant.latencyMs()).isEqualTo(1200);
        assertThat(assistant.finishReason()).isEqualTo("stop");

        // 首条消息更新标题
        verify(sessionRepository).updateTitle(10L, "退货");
        assertThat(ReflectionTestUtils.getField(emitter, "complete")).isEqualTo(true);
    }

    @Test
    void workflowFailureEmitsErrorAndPersistsNoAssistantRow() throws Exception {
        when(agentService.getDetail(1L)).thenReturn(
                new AgentDetailDto(agent(1L, 5L), "豆包", "火山方舟", List.of(), List.of()));
        when(contextStore.loadRecentContext(10L, 20)).thenReturn(List.of());
        when(workflowRunService.start(eq(5L), any()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "工作流执行失败: 变量不存在"));

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(10L, "退货");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).path("type").asText()).isEqualTo("error");
        assertThat(events.get(0).path("content").asText()).contains("变量不存在");
        verify(messageRepository, times(1)).insert(any());
        verify(sessionRepository).updateTitle(10L, "退货");
        assertThat(ReflectionTestUtils.getField(emitter, "complete")).isEqualTo(true);
    }

    @Test
    void normalBranchReversesDescHistoryForwardsDeltasAndPersists() throws Exception {
        // 仓储按 id DESC 返回：id 大的在前
        when(contextStore.loadRecentContext(10L, 20))
                .thenReturn(List.of(message(200L, "assistant", "旧回复"), message(100L, "user", "旧提问")));
        when(llmStreamService.streamChat(any(), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("你");
            onDelta.accept("好");
            return new LlmStreamResult("你好", 10, 2, "stop", 300L);
        });

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(10L, "在吗");

        // 上下文顺序：system → 升序历史 → 当前消息（DESC 必须反转）
        ArgumentCaptor<LlmStreamCmd> cmdCaptor = ArgumentCaptor.forClass(LlmStreamCmd.class);
        verify(llmStreamService).streamChat(cmdCaptor.capture(), any());
        List<LlmChatMessage> messages = cmdCaptor.getValue().messages();
        assertThat(messages.stream().map(LlmChatMessage::role).toList())
                .containsExactly("system", "user", "assistant", "user");
        assertThat(messages.get(0).content()).isEqualTo("你是客服");
        assertThat(messages.get(1).content()).isEqualTo("旧提问");
        assertThat(messages.get(2).content()).isEqualTo("旧回复");
        assertThat(messages.get(3).content()).isEqualTo("在吗");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(contentsOf(events, "delta")).containsExactly("你", "好");
        assertThat(events.get(events.size() - 1).path("type").asText()).isEqualTo("done");

        ArgumentCaptor<ChatMessageDto> msgCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messageRepository, times(2)).insert(msgCaptor.capture());
        ChatMessageDto assistant = msgCaptor.getAllValues().get(1);
        assertThat(assistant.content()).isEqualTo("你好");
        assertThat(assistant.tokens()).isEqualTo(12);
        assertThat(assistant.latencyMs()).isEqualTo(300);
        // 非首条消息不改标题
        verify(sessionRepository, never()).updateTitle(eq(10L), any());
    }

    @Test
    void clientDisconnectDuringStreamAbortsAndLeavesNoAssistantRow() throws Exception {
        // 真实单线程池：worker 在第二段 delta 前等待测试线程把 emitter 标记为已完成
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        try {
            var firstDeltaSent = new java.util.concurrent.CountDownLatch(1);
            var emitterCompleted = new java.util.concurrent.CountDownLatch(1);
            when(contextStore.loadRecentContext(10L, 20)).thenReturn(List.of());
            when(llmStreamService.streamChat(any(), any())).thenAnswer(invocation -> {
                Consumer<String> onDelta = invocation.getArgument(1);
                onDelta.accept("第一段");
                firstDeltaSent.countDown();
                if (!emitterCompleted.await(5, TimeUnit.SECONDS)) {
                    // worker 线程内不能用 AssertionError（doChat 只 catch Exception），抛 RuntimeException
                    throw new RuntimeException("等待测试线程完成 emitter 超时");
                }
                onDelta.accept("第二段"); // emitter 已 complete → IllegalStateException → 中止
                return new LlmStreamResult("不会返回", 0, 0, "stop", 1L);
            });

            SseEmitter emitter = newService(pool).sendMessage(10L, "在吗");
            assertThat(firstDeltaSent.await(5, TimeUnit.SECONDS)).isTrue();
            emitter.complete();
            emitterCompleted.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            List<JsonNode> events = sentEvents(emitter);
            assertThat(contentsOf(events, "delta")).containsExactly("第一段");
            assertThat(events.stream().noneMatch(e -> "error".equals(e.path("type").asText()))).isTrue();
            verify(messageRepository, times(1)).insert(any()); // 只有 user 消息
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingSessionEmitsError() throws Exception {
        when(sessionRepository.findById(404L)).thenReturn(null);

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(404L, "你好");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).path("type").asText()).isEqualTo("error");
        assertThat(events.get(0).path("content").asText()).contains("会话不存在");
        verifyNoInteractions(agentService, workflowRunService, llmStreamService);
        verify(messageRepository, never()).insert(any());
    }

    @Test
    void missingAgentEmitsError() throws Exception {
        when(agentService.getDetail(1L)).thenThrow(new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: 1"));

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(10L, "你好");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(events.get(0).path("content").asText()).contains("Agent 不存在或已删除");
        verify(messageRepository, never()).insert(any());
    }

    @Test
    void disabledAgentEmitsError() throws Exception {
        when(agentService.getDetail(1L)).thenReturn(new AgentDetailDto(
                new AgentDto(1L, "客服 Agent", null, null, 7L, null, 0, null, null, null),
                null, null, List.of(), List.of()));

        SseEmitter emitter = newService(sameThreadExecutor()).sendMessage(10L, "你好");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(events.get(0).path("type").asText()).isEqualTo("error");
        assertThat(events.get(0).path("content").asText()).contains("Agent 已停用");
        verify(messageRepository, never()).insert(any());
    }

    @Test
    void rejectedPoolEmitsBusyErrorWithoutRunningWorker() throws Exception {
        ThreadPoolExecutor rejecting = mock(ThreadPoolExecutor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException("pool full"))
                .when(rejecting).execute(any(Runnable.class));

        SseEmitter emitter = newService(rejecting).sendMessage(10L, "你好");

        List<JsonNode> events = sentEvents(emitter);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).path("type").asText()).isEqualTo("error");
        assertThat(events.get(0).path("content").asText()).contains("服务器繁忙");
        verify(messageRepository, never()).insert(any());
        verifyNoInteractions(agentService);
    }

    @Test
    void listSessionsUsesCursorAndReturnsNextCursor() {
        when(sessionRepository.listByIdCursor(null, 3)).thenReturn(List.of(
                session(20L, 1L), session(19L, 1L), session(18L, 1L)));

        CursorPageDto<ChatSessionDto> page = newService(sameThreadExecutor()).listSessions(null, 2);

        assertThat(page.records().stream().map(ChatSessionDto::id)).containsExactly(20L, 19L);
        assertThat(page.nextCursor()).isEqualTo(19L);
    }

    @Test
    void listMessagesOfMissingSessionThrowsNotFound() {
        when(sessionRepository.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> newService(sameThreadExecutor()).listMessages(404L, null, 20))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(messageRepository, never()).listBySessionCursor(any(), any(), anyInt());
    }
}
