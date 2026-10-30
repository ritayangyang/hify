package com.hify.modules.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.model.api.dto.LlmStreamCmd;
import com.hify.modules.model.api.dto.LlmStreamResult;
import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.service.LlmStreamService;
import com.hify.modules.model.infra.client.LlmStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 流式对话服务：OpenAI 兼容 SSE（data: {json} 帧，[DONE] 结束）。
 * 模型/提供商加载、鉴权、错误转换复用 {@link LlmRequestSupport}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmStreamServiceImpl implements LlmStreamService {

    private static final String DONE_MARKER = "[DONE]";

    private final LlmRequestSupport requestSupport;
    private final LlmStreamClient llmStreamClient;
    private final ObjectMapper objectMapper;

    @Override
    public LlmStreamResult streamChat(LlmStreamCmd cmd, Consumer<String> onDelta) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对话参数不能为空");
        }
        ModelConfigDto model = requestSupport.loadChatModel(cmd.modelConfigId());
        ProviderDto provider = requestSupport.loadEnabledProvider(model.providerId());

        String url = requestSupport.buildChatEndpoint(provider.baseUrl());
        Map<String, String> headers = requestSupport.buildHeaders(provider);
        Map<String, Object> streamOptions = new LinkedHashMap<>();
        streamOptions.put("stream", true);
        // 让上游在末帧回传 usage（OpenAI/方舟兼容）
        streamOptions.put("stream_options", Map.of("include_usage", true));
        String requestBody = requestSupport.buildRequestBody(
                model, cmd.messages(), cmd.temperature(), cmd.maxTokens(), streamOptions);

        StringBuilder fullContent = new StringBuilder();
        UsageAccumulator usage = new UsageAccumulator();
        String[] finishReason = {null};
        long startNanos = System.nanoTime();
        log.info("LLM 调用开始（流式）provider={} model={}", provider.name(), model.modelId());

        try {
            llmStreamClient.postStream(url, headers, requestBody, data -> handleFrame(data, onDelta,
                    fullContent, usage, finishReason));
        } catch (LlmStreamClient.HttpStatusException e) {
            log.warn("LLM 流式调用失败 provider={} status={}", provider.name(), e.getStatus());
            throw requestSupport.upstreamError(e.getStatus(), e.getMessage());
        } catch (IOException e) {
            log.warn("LLM 流式网络失败 providerId={} model={}", provider.id(), model.modelId(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "LLM 网络调用失败: " + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (fullContent.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "LLM 返回内容为空");
        }
        String reason = StringUtils.hasText(finishReason[0]) ? finishReason[0] : "stop";
        log.info("LLM 流式调用成功 provider={} model={} latencyMs={} promptTokens={} completionTokens={}",
                provider.name(), model.modelId(), latencyMs, usage.promptTokens, usage.completionTokens);
        return new LlmStreamResult(fullContent.toString(),
                usage.promptTokens, usage.completionTokens, reason, latencyMs);
    }

    private void handleFrame(String data, Consumer<String> onDelta, StringBuilder fullContent,
                             UsageAccumulator usage, String[] finishReason) {
        if (!StringUtils.hasText(data) || DONE_MARKER.equals(data.trim())) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (IOException e) {
            // 个别心跳/注释帧不是 JSON，忽略即可
            return;
        }
        JsonNode choice = root.path("choices").path(0);
        String delta = choice.path("delta").path("content").asText("");
        if (StringUtils.hasText(delta)) {
            fullContent.append(delta);
            onDelta.accept(delta);
        }
        String reason = choice.path("finish_reason").asText("");
        if (StringUtils.hasText(reason)) {
            finishReason[0] = reason;
        }
        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode() && usageNode.isObject()) {
            usage.promptTokens = Math.max(usage.promptTokens, usageNode.path("prompt_tokens").asInt(0));
            usage.completionTokens = Math.max(usage.completionTokens,
                    usageNode.path("completion_tokens").asInt(0));
        }
    }

    /** 末帧 usage 容器（onDelta 闭包里使用，避免成员变量带来的线程状态问题） */
    private static class UsageAccumulator {
        private int promptTokens;
        private int completionTokens;
    }
}
