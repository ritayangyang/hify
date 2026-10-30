package com.hify.modules.model.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.model.api.dto.ConnectionTestCmd;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.infra.client.LlmHttpClient;
import com.hify.modules.model.infra.client.LlmHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖各协议 baseUrl 拼接，重点保证火山方舟 Ark（/api/v3）不会被错拼成 /v1/models。
 */
class ProviderConnectionTesterTest {

    private LlmHttpClient httpClient;
    private ProviderConnectionTester tester;

    @BeforeEach
    void setUp() {
        httpClient = mock(LlmHttpClient.class);
        tester = new ProviderConnectionTester(httpClient, new ObjectMapper());
    }

    @Test
    void arkBaseUrlWithApiV3AppendsModelsOnly() throws Exception {
        when(httpClient.get(anyString(), anyMap()))
                .thenAnswer(invocation -> new LlmHttpResponse(200,
                        "{\"data\":[{\"id\":\"doubao-pro-32k\"},{\"id\":\"doubao-lite-4k\"}]}"));

        ConnectionTestResult result = tester.test(new ConnectionTestCmd(
                ProviderType.OPENAI_COMPATIBLE,
                "https://ark.cn-beijing.volces.com/api/v3",
                "ark-test-key"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headersCaptor = captorMap();
        verify(httpClient).get(urlCaptor.capture(), headersCaptor.capture());
        assertThat(urlCaptor.getValue())
                .isEqualTo("https://ark.cn-beijing.volces.com/api/v3/models");
        assertThat(headersCaptor.getValue()).containsEntry("Authorization", "Bearer ark-test-key");
        assertThat(result.success()).isTrue();
        assertThat(result.modelCount()).isEqualTo(2);
    }

    @Test
    void plainOpenAiBaseUrlGetsV1ModelsAppended() throws Exception {
        when(httpClient.get(anyString(), anyMap()))
                .thenReturn(new LlmHttpResponse(200, "{\"data\":[]}"));

        tester.test(new ConnectionTestCmd(ProviderType.OPENAI, "https://api.openai.com", "sk-x"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(urlCaptor.capture(), anyMap());
        assertThat(urlCaptor.getValue()).isEqualTo("https://api.openai.com/v1/models");
    }

    @Test
    void baseUrlEndingWithV1AppendsModelsOnly() throws Exception {
        when(httpClient.get(anyString(), anyMap()))
                .thenReturn(new LlmHttpResponse(200, "{\"data\":[]}"));

        tester.test(new ConnectionTestCmd(ProviderType.OPENAI_COMPATIBLE,
                "https://api.deepseek.com/v1/", "sk-x"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(urlCaptor.capture(), anyMap());
        // 末尾斜杠先被去除，再按已有版本段拼接
        assertThat(urlCaptor.getValue()).isEqualTo("https://api.deepseek.com/v1/models");
    }

    @Test
    void missingApiKeyReturnsBusinessFailureWithoutHttpRequest() {
        ConnectionTestResult result = tester.test(new ConnectionTestCmd(
                ProviderType.OPENAI_COMPATIBLE,
                "https://ark.cn-beijing.volces.com/api/v3",
                "  "));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("apiKey 不能为空");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, String>> captorMap() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
