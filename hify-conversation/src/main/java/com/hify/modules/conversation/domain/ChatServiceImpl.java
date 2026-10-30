package com.hify.modules.conversation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.api.dto.AgentDetailDto;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.api.service.AgentService;
import com.hify.modules.conversation.api.dto.ChatMessageDto;
import com.hify.modules.conversation.api.dto.ChatSessionDto;
import com.hify.modules.conversation.api.dto.CreateSessionCmd;
import com.hify.modules.conversation.api.dto.CursorPageDto;
import com.hify.modules.conversation.api.service.ChatService;
import com.hify.modules.conversation.domain.context.ChatContextStore;
import com.hify.modules.conversation.domain.repository.ChatMessageRepository;
import com.hify.modules.conversation.domain.repository.ChatSessionRepository;
import com.hify.modules.model.api.dto.LlmChatMessage;
import com.hify.modules.model.api.dto.LlmStreamCmd;
import com.hify.modules.model.api.dto.LlmStreamResult;
import com.hify.modules.model.api.service.LlmStreamService;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunStartCmd;
import com.hify.modules.workflow.api.service.WorkflowRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 对话服务实现。
 *
 * <p>请求线程只创建 {@link SseEmitter} 并把对话任务提交到 llmStreamExecutor，立即返回；
 * 对话在流式线程池的工作线程上执行。Agent 绑定工作流时走工作流分支（结果整段下发，
 * 工作流本身非流式），否则走普通 LLM 流式分支。
 *
 * <p>用户消息在调用下游前先落库且不随失败回滚；任何失败只推 error 事件，
 * 绝不写入孤儿 assistant 消息。
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    /** SSE 整体超时：工作流分支最坏约 180s，取 200s 留余量 */
    private static final long SSE_TIMEOUT_MS = 200_000L;

    /** 上下文装载的最近消息条数（约 10 轮） */
    private static final int CONTEXT_MESSAGE_LIMIT = 20;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TITLE_MAX_LENGTH = 20;
    private static final String DEFAULT_TITLE = "新会话";
    private static final String SESSION_ACTIVE = "ACTIVE";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String FINISH_REASON_STOP = "stop";

    /** 用户消息映射为工作流入参时使用的固定键 */
    private static final String WORKFLOW_INPUT_USER_MESSAGE = "userMessage";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatContextStore contextStore;
    private final AgentService agentService;
    private final LlmStreamService llmStreamService;
    private final WorkflowRunService workflowRunService;
    private final ThreadPoolExecutor llmStreamExecutor;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            ChatContextStore contextStore,
            AgentService agentService,
            LlmStreamService llmStreamService,
            WorkflowRunService workflowRunService,
            @Qualifier("llmStreamExecutor") ThreadPoolExecutor llmStreamExecutor,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.contextStore = contextStore;
        this.agentService = agentService;
        this.llmStreamService = llmStreamService;
        this.workflowRunService = workflowRunService;
        this.llmStreamExecutor = llmStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ChatSessionDto createSession(CreateSessionCmd cmd) {
        if (cmd == null || cmd.agentId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "agentId 不能为空");
        }
        // Agent 不存在时 getDetail 抛 NOT_FOUND，由全局异常处理转 404
        AgentDto agent = agentService.getDetail(cmd.agentId()).agent();
        if (agent.status() == null || agent.status() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 已停用: " + agent.name());
        }
        return sessionRepository.insert(
                new ChatSessionDto(null, cmd.agentId(), DEFAULT_TITLE, SESSION_ACTIVE, null, null));
    }

    @Override
    public CursorPageDto<ChatSessionDto> listSessions(Long cursorId, Integer size) {
        int limit = normalizeSize(size);
        List<ChatSessionDto> rows = sessionRepository.listByIdCursor(cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChatSessionDto> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        return CursorPageDto.of(page, nextCursor);
    }

    @Override
    @Transactional
    public void deleteSession(Long id) {
        ChatSessionDto session = sessionRepository.findById(id);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + id);
        }
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
    }

    @Override
    public CursorPageDto<ChatMessageDto> listMessages(Long sessionId, Long cursorId, Integer size) {
        if (sessionRepository.findById(sessionId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        int limit = normalizeSize(size);
        List<ChatMessageDto> rows = messageRepository.listBySessionCursor(sessionId, cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChatMessageDto> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        return CursorPageDto.of(page, nextCursor);
    }

    @Override
    public SseEmitter sendMessage(Long sessionId, String content) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 容器超时兜底：标记终态，工作线程下一次 send 会感知到断开
        emitter.onTimeout(emitter::complete);
        try {
            llmStreamExecutor.execute(() -> doChat(sessionId, content, emitter));
        } catch (RejectedExecutionException e) {
            // 流式池 AbortPolicy：队列满直接拒绝，由请求线程给用户一个明确提示
            log.warn("流式对话线程池已满，拒绝任务 sessionId={}", sessionId);
            safeSendError(emitter, "服务器繁忙，请稍后再试");
        }
        return emitter;
    }

    /**
     * 流式线程池工作线程主体。任何出口都必须让 emitter 进入终态。
     */
    private void doChat(Long sessionId, String userContent, SseEmitter emitter) {
        try {
            ChatSessionDto session = sessionRepository.findById(sessionId);
            if (session == null) {
                safeSendError(emitter, "会话不存在: " + sessionId);
                return;
            }
            AgentDto agent = loadChatAgent(session.agentId(), emitter);
            if (agent == null) {
                // error 事件已推送
                return;
            }
            log.info("对话开始 sessionId={} agentId={} branch={}",
                    sessionId, agent.id(), agent.workflowId() != null ? "workflow" : "normal");

            // 先取历史：首条判定 + 上下文组装共用一次查询；返回为 id 倒序
            List<ChatMessageDto> recentDesc = contextStore.loadRecentContext(sessionId, CONTEXT_MESSAGE_LIMIT);
            boolean firstMessage = recentDesc.isEmpty();
            List<ChatMessageDto> history = new ArrayList<>(recentDesc);
            Collections.reverse(history);

            // 用户消息先落库：下游失败也不回滚（与教程一致，保留用户输入）
            messageRepository.insert(new ChatMessageDto(
                    null, sessionId, ROLE_USER, userContent, null, null, null, null));
            if (firstMessage) {
                sessionRepository.updateTitle(sessionId, buildTitle(userContent));
            }

            if (agent.workflowId() != null) {
                runWorkflowChat(agent, userContent, sessionId, emitter);
            } else {
                runNormalChat(agent, userContent, history, sessionId, emitter);
            }
        } catch (ChatClientAbortedException e) {
            log.info("SSE 客户端断开，结束本次对话 sessionId={}", sessionId);
            quietComplete(emitter);
        } catch (Exception e) {
            // 工作流/LLM 的 BizException 与未预期异常统一在此转 error 事件；
            // assistant 消息只在各分支成功路径落库，此处不会产生孤儿消息
            log.error("对话处理失败 sessionId={}", sessionId, e);
            safeSendError(emitter, friendlyMessage(e));
        }
    }

    /** 加载并校验对话用 Agent；不满足时推送 error 事件并返回 null */
    private AgentDto loadChatAgent(Long agentId, SseEmitter emitter) {
        AgentDetailDto detail;
        try {
            detail = agentService.getDetail(agentId);
        } catch (BizException e) {
            safeSendError(emitter, "绑定的 Agent 不存在或已删除");
            return null;
        }
        AgentDto agent = detail.agent();
        if (agent.status() == null || agent.status() != 1) {
            safeSendError(emitter, "Agent 已停用: " + agent.name());
            return null;
        }
        return agent;
    }

    /**
     * 工作流分支（教程 26 章）：用户消息固定映射为 inputs.userMessage。
     * 工作流同步执行、非流式，成功后把完整输出作为一个 delta 整段下发。
     * start 抛异常时直接冒泡到 doChat 统一推 error 事件，不写 assistant 消息。
     */
    private void runWorkflowChat(AgentDto agent, String userContent, Long sessionId, SseEmitter emitter) {
        long startNanos = System.nanoTime();
        log.info("工作流调用开始 sessionId={} workflowId={}", sessionId, agent.workflowId());
        WorkflowRunDetailDto detail = workflowRunService.start(agent.workflowId(),
                new WorkflowRunStartCmd(Map.of(WORKFLOW_INPUT_USER_MESSAGE, userContent)));
        long latencyMs = elapsedMillis(startNanos);

        String output = detail.run().output();
        if (!StringUtils.hasText(output)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "工作流未返回内容");
        }
        sendJson(emitter, SseEvent.delta(output));
        Integer workflowElapsed = detail.run().elapsedMs();
        messageRepository.insert(new ChatMessageDto(
                null, sessionId, ROLE_ASSISTANT, output,
                detail.run().totalTokens(), FINISH_REASON_STOP,
                workflowElapsed != null ? workflowElapsed : (int) latencyMs, null));
        log.info("工作流调用成功 sessionId={} workflowId={} latencyMs={} tokens={}",
                sessionId, agent.workflowId(), latencyMs, detail.run().totalTokens());
        sendJson(emitter, SseEvent.done(FINISH_REASON_STOP, latencyMs));
        emitter.complete();
    }

    /** 普通分支：systemPrompt + 最近 20 条历史 + 当前消息，走 LLM 流式调用并逐段转发 */
    private void runNormalChat(AgentDto agent, String userContent, List<ChatMessageDto> history,
                               Long sessionId, SseEmitter emitter) {
        log.info("普通对话分支开始 sessionId={} modelConfigId={} historyMessages={}",
                sessionId, agent.modelConfigId(), history.size());
        List<LlmChatMessage> messages = new ArrayList<>(history.size() + 2);
        if (StringUtils.hasText(agent.systemPrompt())) {
            messages.add(new LlmChatMessage(ROLE_SYSTEM, agent.systemPrompt().trim()));
        }
        for (ChatMessageDto historical : history) {
            messages.add(new LlmChatMessage(historical.role(), historical.content()));
        }
        messages.add(LlmChatMessage.user(userContent));

        LlmStreamCmd cmd = new LlmStreamCmd(
                agent.modelConfigId(),
                List.copyOf(messages),
                decimalParam(agent.modelParams(), "temperature"),
                // Agent 表单写 maxTokens（驼峰），兼容 DDL 注释中的 max_tokens 写法
                firstNonNull(
                        intParam(agent.modelParams(), "maxTokens"),
                        intParam(agent.modelParams(), "max_tokens")));

        // onDelta 内 send 失败（客户端断开）抛 ChatClientAbortedException，
        // 模型层会据此取消上游 HTTP 调用
        LlmStreamResult result = llmStreamService.streamChat(cmd,
                delta -> sendJson(emitter, SseEvent.delta(delta)));

        messageRepository.insert(new ChatMessageDto(
                null, sessionId, ROLE_ASSISTANT, result.content(),
                result.promptTokens() + result.completionTokens(),
                result.finishReason(), saturatingInt(result.latencyMs()), null));
        sendJson(emitter, SseEvent.done(result.finishReason(), result.latencyMs()));
        emitter.complete();
    }

    /** 发送一个 SSE 事件；发送失败（客户端断开/emitter 终态）转中止异常 */
    private void sendJson(SseEmitter emitter, SseEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 自有事件结构序列化失败属于程序缺陷，不是客户端问题
            throw new IllegalStateException("SSE 事件序列化失败", e);
        }
        try {
            emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ChatClientAbortedException("SSE 发送失败，客户端可能已断开: " + e.getMessage());
        }
    }

    /** error 路径：发送失败也不再抛（连接可能已断），保证 complete */
    private void safeSendError(SseEmitter emitter, String message) {
        try {
            sendJson(emitter, SseEvent.error(message));
        } catch (Exception ignored) {
            // 忽略断开场景下的二次发送失败
        }
        quietComplete(emitter);
    }

    private void quietComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已终态/已断开
        }
    }

    private String friendlyMessage(Exception e) {
        if (e instanceof BizException bizException) {
            return bizException.getMessage();
        }
        return "对话处理失败，请稍后再试";
    }

    private String buildTitle(String content) {
        String oneLine = content.strip().replaceAll("\\s+", " ");
        return oneLine.length() > TITLE_MAX_LENGTH ? oneLine.substring(0, TITLE_MAX_LENGTH) : oneLine;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private int saturatingInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private BigDecimal decimalParam(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Agent 参数 {} 不是合法数字: {}", key, value);
            return null;
        }
    }

    private Integer intParam(Map<String, Object> params, String key) {
        BigDecimal decimal = decimalParam(params, key);
        return decimal == null ? null : decimal.intValue();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * SSE 统一事件载荷，null 字段不输出，实际形态：
     * delta {"type":"delta","content":"..."}、
     * done  {"type":"done","finishReason":"stop","latencyMs":1234}、
     * error {"type":"error","content":"..."}。
     */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record SseEvent(
            String type,
            String content,
            String finishReason,
            Long latencyMs) {

        static SseEvent delta(String content) {
            return new SseEvent("delta", content, null, null);
        }

        static SseEvent done(String finishReason, long latencyMs) {
            return new SseEvent("done", null, finishReason, latencyMs);
        }

        static SseEvent error(String content) {
            return new SseEvent("error", content, null, null);
        }
    }
}
