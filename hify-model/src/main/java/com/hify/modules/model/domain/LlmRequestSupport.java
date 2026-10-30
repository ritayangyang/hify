package com.hify.modules.model.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.model.api.dto.LlmChatMessage;
import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.domain.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 调用的共用准备逻辑：模型/提供商加载、端点拼接、鉴权头、请求体组装、上游错误解析。
 * 非流式 {@link LlmChatServiceImpl} 与流式 {@link LlmStreamServiceImpl} 复用，保证
 * 4xx/5xx 判定、Key 缺失处理等规则只有一处事实来源。
 */
@Component
@RequiredArgsConstructor
public class LlmRequestSupport {

    public static final String API_KEY_FIELD = "apiKey";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String OPENAI_VERSION_PREFIX = "/v1";

    /** baseUrl 末尾的版本段（/v1、/v3、/api/v3 等，如火山方舟 Ark） */
    private static final Pattern VERSION_SUFFIX = Pattern.compile("/v\\d+$", Pattern.CASE_INSENSITIVE);

    private final ModelConfigRepository modelConfigRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    /** 加载已启用的对话模型，任一条件不满足抛 BizException */
    public ModelConfigDto loadChatModel(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "modelConfigId 不能为空");
        }
        List<ModelConfigDto> models = modelConfigRepository.listByIds(List.of(modelConfigId));
        if (models.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定模型不存在: " + modelConfigId);
        }
        ModelConfigDto model = models.get(0);
        if (!Integer.valueOf(1).equals(model.status())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定模型已停用: " + model.name());
        }
        if (!Integer.valueOf(1).equals(model.modelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定模型不是对话模型: " + model.name());
        }
        return model;
    }

    public ProviderDto loadEnabledProvider(Long providerId) {
        ProviderDto provider = providerRepository.findById(providerId);
        if (provider == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "模型所属提供商不存在: " + providerId);
        }
        if (!Integer.valueOf(1).equals(provider.status())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型所属提供商已停用: " + provider.name());
        }
        if (provider.type() == ProviderType.ANTHROPIC) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "暂不支持 Anthropic 原生协议执行，请改用 OpenAI 兼容提供商: " + provider.name());
        }
        return provider;
    }

    public String buildChatEndpoint(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提供商 API 地址未配置");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 已带版本段直接拼 /chat/completions，否则补 /v1（Ollama 同样开放 /v1 兼容入口）
        return VERSION_SUFFIX.matcher(base).find()
                ? base + CHAT_COMPLETIONS_PATH
                : base + OPENAI_VERSION_PREFIX + CHAT_COMPLETIONS_PATH;
    }

    public Map<String, String> buildHeaders(ProviderDto provider) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        // OLLAMA 本地服务无认证；OPENAI / OPENAI_COMPATIBLE 走 Bearer
        if (provider.type() != ProviderType.OLLAMA) {
            Object apiKey = provider.authConfig() == null ? null : provider.authConfig().get(API_KEY_FIELD);
            if (apiKey == null || !StringUtils.hasText(apiKey.toString())) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "提供商未配置 API Key: " + provider.name());
            }
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    /**
     * 组装 OpenAI 兼容请求体。
     *
     * @param extraTopLevel 额外顶层字段（如 stream:true、stream_options），可为 null
     */
    public String buildRequestBody(ModelConfigDto model, List<LlmChatMessage> messages,
                                   BigDecimal temperature, Integer maxTokens,
                                   Map<String, Object> extraTopLevel) {
        if (messages == null || messages.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对话消息不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.modelId());
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        if (extraTopLevel != null) {
            body.putAll(extraTopLevel);
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "对话请求序列化失败", e);
        }
    }

    /** 非 2xx 响应转业务异常：4xx→参数错误，其余→内部错误（上游不可用） */
    public BizException upstreamError(int httpStatus, String responseBody) {
        String upstreamMessage = extractUpstreamErrorMessage(responseBody);
        String message = "LLM 调用失败(HTTP " + httpStatus + ")"
                + (StringUtils.hasText(upstreamMessage) ? ": " + upstreamMessage : "");
        ErrorCode errorCode = httpStatus >= 400 && httpStatus < 500
                ? ErrorCode.PARAM_ERROR : ErrorCode.INTERNAL_ERROR;
        return new BizException(errorCode, message);
    }

    /** 取 OpenAI 风格错误体 error.message；取不到返回 null */
    public String extractUpstreamErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText("");
            return StringUtils.hasText(message) ? message : null;
        } catch (IOException e) {
            return null;
        }
    }
}
