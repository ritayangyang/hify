package com.hify.modules.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.model.api.dto.LlmChatCmd;
import com.hify.modules.model.api.dto.LlmChatResult;
import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.service.LlmChatService;
import com.hify.modules.model.infra.client.LlmChatClient;
import com.hify.modules.model.infra.client.LlmHttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 非流式对话服务：按模型配置找到提供商，按 OpenAI 兼容协议发起 chat/completions。
 * 当前支持 OPENAI / OPENAI_COMPATIBLE / OLLAMA（Ollama 提供 /v1 兼容入口）；
 * Anthropic 原生协议暂不支持。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmChatServiceImpl implements LlmChatService {

    private final LlmRequestSupport requestSupport;
    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;

    @Override
    public LlmChatResult chat(LlmChatCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对话参数不能为空");
        }

        ModelConfigDto model = requestSupport.loadChatModel(cmd.modelConfigId());
        ProviderDto provider = requestSupport.loadEnabledProvider(model.providerId());

        String url = requestSupport.buildChatEndpoint(provider.baseUrl());
        var headers = requestSupport.buildHeaders(provider);
        String requestBody = requestSupport.buildRequestBody(
                model, cmd.messages(), cmd.temperature(), cmd.maxTokens(), null);

        long startNanos = System.nanoTime();
        log.info("LLM 调用开始（非流式）provider={} model={}", provider.name(), model.modelId());
        LlmHttpResponse response;
        try {
            response = llmChatClient.postJson(url, headers, requestBody);
        } catch (IOException e) {
            // 不记录 url/headers，避免 apiKey 经 Referer 等字段泄漏
            log.warn("LLM 网络调用失败 providerId={} model={}", provider.id(), model.modelId(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "LLM 网络调用失败: " + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (response.status() < 200 || response.status() >= 300) {
            log.warn("LLM 调用失败 provider={} status={} latencyMs={}",
                    provider.name(), response.status(), latencyMs);
            throw requestSupport.upstreamError(response.status(), response.body());
        }

        LlmChatResult result = parseSuccessBody(response.body(), latencyMs);
        log.info("LLM 调用成功 provider={} model={} latencyMs={} promptTokens={} completionTokens={}",
                provider.name(), model.modelId(), latencyMs, result.promptTokens(), result.completionTokens());
        return result;
    }

    private LlmChatResult parseSuccessBody(String body, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "LLM 返回内容为空");
            }
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
            return new LlmChatResult(content, promptTokens, completionTokens, totalTokens, latencyMs);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "LLM 响应解析失败", e);
        }
    }
}
