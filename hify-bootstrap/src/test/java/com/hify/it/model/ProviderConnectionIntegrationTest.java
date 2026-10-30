package com.hify.it.model;

import com.hify.it.support.AbstractIntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 提供商连通性测试接口集成测试（P1-4）：
 * 外部模型服务由 MockWebServer 替掉，验证真实 OkHttp 调用路径、健康行回写，
 * 以及掩码更新后实际鉴权仍使用旧明文密钥（P0-6 掩码回填的运行期佐证）。
 */
class ProviderConnectionIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/providers";
    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start(0);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    private Long createProvider(String code, String rawKey) throws Exception {
        postJson(BASE, Map.of(
                "code", code,
                "type", 1, // OPENAI
                "name", "IT Conn Provider",
                "baseUrl", server.url("/").toString(),
                "authConfig", Map.of("apiKey", rawKey)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_provider WHERE code = ? AND deleted = 0", Long.class, code);
    }

    @Test
    void should_recordHealthAndUsePreservedKey_when_savedProviderConnectionTestSucceeds() throws Exception {
        // Given：上游模型列表返回 2 条；提供商创建后做一次只回传 ****** 掩码的更新
        String rawKey = "sk-test-conn-ok-001";
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"data\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}"));
        Long id = createProvider("it-conn-ok", rawKey);
        putJson(BASE + "/{0}", Map.of(
                "name", "IT Conn Renamed",
                "authConfig", Map.of("apiKey", "******"))).andExpect(status().isOk());

        // When：对已保存提供商做连通测试
        String response = postNoBody(BASE + "/{0}/test-connection", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.modelCount").value(2))
                .andReturn().getResponse().getContentAsString();

        // Then：实际请求路径与 Bearer 头使用掩码更新前的旧明文密钥
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v1/models");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer " + rawKey);

        // 健康行：失败清零、延迟与成功时间已写
        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT fail_count, (latency_ms IS NOT NULL) AS latency_present, "
                        + "(last_success_at IS NOT NULL) AS success_present "
                        + "FROM t_provider_health WHERE provider_id = ?", id);
        assertThat(((Number) health.get("fail_count")).intValue()).isZero();
        assertThat(((Number) health.get("latency_present")).intValue()).isEqualTo(1);
        assertThat(((Number) health.get("success_present")).intValue()).isEqualTo(1);

        // 响应体不泄漏明文
        assertThat(response).doesNotContain(rawKey);
    }

    @Test
    void should_incrementFailCountAndKeepLastSuccessNull_when_savedProviderConnectionTestFails() throws Exception {
        // Given：上游返回 500
        String rawKey = "sk-test-conn-fail-001";
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        Long id = createProvider("it-conn-fail", rawKey);

        // When
        String response = postNoBody(BASE + "/{0}/test-connection", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.errorMessage").value("HTTP 500"))
                .andReturn().getResponse().getContentAsString();

        // Then：fail_count +1、latency 写入、last_success 保持 NULL；错误响应不泄密
        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT fail_count, (latency_ms IS NOT NULL) AS latency_present, "
                        + "(last_success_at IS NULL) AS success_null "
                        + "FROM t_provider_health WHERE provider_id = ?", id);
        assertThat(((Number) health.get("fail_count")).intValue()).isEqualTo(1);
        assertThat(((Number) health.get("latency_present")).intValue()).isEqualTo(1);
        assertThat(((Number) health.get("success_null")).intValue()).isEqualTo(1);
        assertThat(response).doesNotContain(rawKey);
    }

    @Test
    void should_notWriteHealthAndCallOllamaTags_when_adhocConnectionTest() throws Exception {
        // Given：OLLAMA 无 key 可直接测；OPENAI 缺 key 在发 HTTP 前业务失败
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"models\":[{\"name\":\"llama\"}]}"));

        // When：OLLAMA 即席测试成功
        postJson(BASE + "/connection-test", Map.of(
                "type", 4, // OLLAMA
                "baseUrl", server.url("/").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.modelCount").value(1));

        // OPENAI 缺 key：HTTP 200 信封内 success=false（连通测试器对配置错误的契约）
        postJson(BASE + "/connection-test", Map.of(
                "type", 1,
                "baseUrl", server.url("/").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.errorMessage").value("apiKey 不能为空"));

        // Then：即席测试不落任何提供商/健康行数据；上游只收到 OLLAMA 那一次请求
        Long providerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider", Long.class);
        Long healthCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider_health", Long.class);
        assertThat(providerCount).isZero();
        assertThat(healthCount).isZero();
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server.takeRequest(5, TimeUnit.SECONDS).getPath()).isEqualTo("/api/tags");
    }
}
