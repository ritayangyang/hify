package com.hify.modules.model.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.model.api.dto.ConnectionTestCmd;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.infra.client.LlmHttpClient;
import com.hify.modules.model.infra.client.LlmHttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 提供商连通性测试器：按 {@link ProviderType} 分发到不同协议。
 * <ul>
 *   <li>OPENAI / OPENAI_COMPATIBLE：GET {baseUrl}/v1/models，Bearer Token；
 *       baseUrl 已带版本后缀（/v1、/api/v3 等，如火山方舟）时直接拼 /models</li>
 *   <li>ANTHROPIC：GET {baseUrl}/v1/models，x-api-key + anthropic-version</li>
 *   <li>OLLAMA：GET {baseUrl}/api/tags，无认证</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderConnectionTester {

    /** Anthropic 要求的版本头 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** baseUrl 末尾的版本段（/v1、/v3、/api/v3 等，如火山方舟 Ark） */
    private static final Pattern VERSION_SUFFIX = Pattern.compile("/v\\d+$", Pattern.CASE_INSENSITIVE);

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    public ConnectionTestResult test(ConnectionTestCmd cmd) {
        ProviderType type = cmd.type();
        String baseUrl = cmd.baseUrl();
        long startNanos = System.nanoTime();

        if (type == null) {
            return fail(startNanos, "提供商类型不能为空");
        }
        if (!StringUtils.hasText(baseUrl)) {
            return fail(startNanos, "baseUrl 不能为空");
        }

        try {
            String url = buildUrl(type, baseUrl.trim());
            Map<String, String> headers = buildHeaders(type, cmd.apiKey());
            LlmHttpResponse response = llmHttpClient.get(url, headers);
            long latencyMs = elapsedMillis(startNanos);

            if (response.status() < 200 || response.status() >= 300) {
                return ConnectionTestResult.fail(latencyMs, "HTTP " + response.status());
            }

            int modelCount = parseModelCount(type, response.body());
            return ConnectionTestResult.ok(latencyMs, modelCount);
        } catch (IllegalArgumentException e) {
            // 配置类错误（如缺少 apiKey）
            return fail(startNanos, e.getMessage());
        } catch (JsonProcessingException e) {
            log.warn("连通性测试响应解析失败 type={} baseUrl={}", type, baseUrl, e);
            return fail(startNanos, "响应解析失败");
        } catch (IOException e) {
            log.warn("连通性测试网络失败 type={} baseUrl={}", type, baseUrl, e);
            return fail(startNanos, "连接失败: " + e.getMessage());
        }
    }

    private String buildUrl(ProviderType type, String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return switch (type) {
            case OPENAI, OPENAI_COMPATIBLE, ANTHROPIC ->
                // 已带版本段（/v1、/api/v3 等，如火山方舟 Ark）直接拼 /models，
                // 否则按 OpenAI 惯例补 /v1/models
                    VERSION_SUFFIX.matcher(base).find() ? base + "/models" : base + "/v1/models";
            case OLLAMA -> base + "/api/tags";
        };
    }

    private Map<String, String> buildHeaders(ProviderType type, String apiKey) {
        Map<String, String> headers = new HashMap<>();
        switch (type) {
            case OPENAI, OPENAI_COMPATIBLE -> {
                if (!StringUtils.hasText(apiKey)) {
                    throw new IllegalArgumentException("apiKey 不能为空");
                }
                headers.put("Authorization", "Bearer " + apiKey);
            }
            case ANTHROPIC -> {
                if (!StringUtils.hasText(apiKey)) {
                    throw new IllegalArgumentException("apiKey 不能为空");
                }
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", ANTHROPIC_VERSION);
            }
            case OLLAMA -> {
                // 本地服务，无认证
            }
        }
        return headers;
    }

    private int parseModelCount(ProviderType type, String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode array = switch (type) {
            case OPENAI, OPENAI_COMPATIBLE, ANTHROPIC -> root.path("data");
            case OLLAMA -> root.path("models");
        };
        return array.isArray() ? array.size() : 0;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private ConnectionTestResult fail(long startNanos, String message) {
        return ConnectionTestResult.fail(elapsedMillis(startNanos), message);
    }
}
