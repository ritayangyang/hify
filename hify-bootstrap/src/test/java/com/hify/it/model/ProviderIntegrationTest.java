package com.hify.it.model;

import com.hify.it.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型提供商模块集成测试（P0-6 / P1-5 / 参数校验）：
 * Controller → Service → Mapper → 真实 MySQL(hify_test) 全链路。
 * 全部用例事务自动回滚；密钥统一 sk-test-xxx，不打日志、不出现在响应体。
 */
class ProviderIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/providers";

    private Map<String, Object> createBody(String code, String name, String baseUrl, String apiKey) {
        return Map.of(
                "code", code,
                "type", 1, // OPENAI，线上 JSON 形态为数字编码（@JsonValue）
                "name", name,
                "baseUrl", baseUrl,
                "authConfig", Map.of("apiKey", apiKey));
    }

    private Long providerId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_provider WHERE code = ? AND deleted = 0",
                Long.class, code);
    }

    private String rawAuthConfig(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT auth_config FROM t_provider WHERE id = ?", String.class, id);
    }

    // ---------------------------------------------------------------
    // P0-6 凭据双契约：明文落库 + 出参脱敏 + 健康行初始化
    // ---------------------------------------------------------------

    @Test
    void should_persistPlaintextKeyAndInitHealth_when_createSucceeds() throws Exception {
        // Given：一个携带明文 apiKey 的创建请求
        String rawKey = "sk-test-create-001";

        // When
        String response = postJson(BASE,
                createBody("it-create", "IT Create Provider", "https://models.example.com", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.authConfig.apiKey").value("******"))
                .andReturn().getResponse().getContentAsString();

        // Then：HTTP 响应任何位置都不得出现明文密钥
        assertThat(response).doesNotContain(rawKey);

        // 库内契约：auth_config 为明文，status 缺省归一化为启用
        Long id = providerId("it-create");
        assertThat(id).isNotNull();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT type, status, deleted FROM t_provider WHERE id = ?", id);
        assertThat(((Number) row.get("type")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("deleted")).intValue()).isZero();
        assertThat(rawAuthConfig(id)).contains(rawKey);

        // 同事务初始化健康行：0 失败、无延迟、无成功时间
        Map<String, Object> health = jdbcTemplate.queryForMap(
                "SELECT fail_count, (latency_ms IS NULL) AS latency_null, "
                        + "(last_success_at IS NULL) AS success_null, deleted "
                        + "FROM t_provider_health WHERE provider_id = ?", id);
        assertThat(((Number) health.get("fail_count")).intValue()).isZero();
        assertThat(((Number) health.get("latency_null")).intValue()).isEqualTo(1);
        assertThat(((Number) health.get("success_null")).intValue()).isEqualTo(1);
        assertThat(((Number) health.get("deleted")).intValue()).isZero();
    }

    @Test
    void should_rejectDuplicateCode_when_createTwice() throws Exception {
        // Given：code 已存在
        String rawKey = "sk-test-dup-001";
        postJson(BASE, createBody("it-dup", "IT Dup One", "https://a.example.com", rawKey))
                .andExpect(status().isOk());

        // When：同 code 再建一个
        String response = postJson(BASE,
                createBody("it-dup", "IT Dup Two", "https://b.example.com", "sk-test-dup-002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andReturn().getResponse().getContentAsString();

        // Then：错误响应不泄漏任何密钥；只落一条提供商与一条健康行
        assertThat(response).doesNotContain(rawKey);
        assertThat(count("SELECT COUNT(*) FROM t_provider WHERE code = 'it-dup'")).isEqualTo(1L);
        assertThat(count(
                "SELECT COUNT(*) FROM t_provider_health h JOIN t_provider p ON p.id = h.provider_id "
                        + "WHERE p.code = 'it-dup'")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // 参数校验：@Valid + GlobalExceptionHandler 的真实 400 信封
    // ---------------------------------------------------------------

    @Test
    void should_return400_when_createRequestInvalid() throws Exception {
        // Given：type 缺失 / name 空白 / baseUrl 空白 / 坏 JSON 四类非法报文
        // When + Then：全部 400 且走统一 Result 信封
        postJson(BASE, Map.of("code", "it-invalid", "name", "x", "baseUrl", "u"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        postJson(BASE, Map.of("type", 1, "name", "  ", "baseUrl", "u"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        postJson(BASE, Map.of("type", 1, "name", "n", "baseUrl", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        postRaw(BASE, "{\"type\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // 没有任何非法数据落库
        assertThat(count("SELECT COUNT(*) FROM t_provider WHERE code = 'it-invalid'")).isZero();
    }

    @Test
    void should_return400_when_queryParamsInvalid() throws Exception {
        // Given + When + Then：size 超上限 / page 非法 / type 无法识别
        getJson(BASE + "?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        getJson(BASE + "?page=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        getJson(BASE + "?type=BOGUS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------------------------------------------------------------
    // 读接口：脱敏贯穿分页/详情；逻辑删除数据不可见；不存在 → 404
    // ---------------------------------------------------------------

    @Test
    void should_maskKeyInPageAndDetail_andHideDeleted_when_query() throws Exception {
        // Given
        String rawKey = "sk-test-page-001";
        postJson(BASE, createBody("it-page", "ItMaskQueryUniqueName",
                "https://page.example.com", rawKey))
                .andExpect(status().isOk());
        Long id = providerId("it-page");

        // When + Then：分页按唯一名称过滤，恰好一条且密钥脱敏
        String pageResponse = getJson(BASE + "?name=ItMaskQueryUniqueName")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].authConfig.apiKey").value("******"))
                .andReturn().getResponse().getContentAsString();
        assertThat(pageResponse).doesNotContain(rawKey);

        // 详情：provider 脱敏、configs 空列表、health 默认 0 失败
        String detailResponse = getJson(BASE + "/{0}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider.authConfig.apiKey").value("******"))
                .andExpect(jsonPath("$.data.configs").isArray())
                .andExpect(jsonPath("$.data.health.failCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(detailResponse).doesNotContain(rawKey);

        // 不存在的提供商 → 404
        getJson(BASE + "/99999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------------------------------------------------------------
    // P0-6 掩码回填：****** 保留旧密钥；新值覆盖；空字段合并；不存在 → 404
    // ---------------------------------------------------------------

    @Test
    void should_preserveOldKeyOnMaskAndOverwriteOnNewKey_when_update() throws Exception {
        // Given：已存在带密钥的提供商
        String oldKey = "sk-test-update-old";
        String newKey = "sk-test-update-new";
        postJson(BASE, createBody("it-update", "IT Update Before",
                "https://update.example.com", oldKey))
                .andExpect(status().isOk());
        Long id = providerId("it-update");

        // When：编辑只改名称，authConfig 回传 ****** 掩码
        String maskedUpdate = putJson(BASE + "/{0}", Map.of(
                        "name", "IT Update After",
                        "authConfig", Map.of("apiKey", "******")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authConfig.apiKey").value("******"))
                .andReturn().getResponse().getContentAsString();

        // Then：库内仍是旧明文密钥（不是字面量 ******），名称更新，其余字段保留
        assertThat(maskedUpdate).doesNotContain(oldKey);
        assertThat(rawAuthConfig(id)).contains(oldKey).doesNotContain("******");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, base_url, type FROM t_provider WHERE id = ?", id);
        assertThat(row.get("name")).isEqualTo("IT Update After");
        assertThat(row.get("base_url")).isEqualTo("https://update.example.com");
        assertThat(((Number) row.get("type")).intValue()).isEqualTo(1);

        // When：传入新密钥
        String realUpdate = putJson(BASE + "/{0}", Map.of(
                        "authConfig", Map.of("apiKey", newKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authConfig.apiKey").value("******"))
                .andReturn().getResponse().getContentAsString();

        // Then：新密钥落库覆盖旧值
        assertThat(realUpdate).doesNotContain(newKey);
        assertThat(rawAuthConfig(id)).contains(newKey).doesNotContain(oldKey);

        // 更新不存在的提供商 → 404
        putJson(BASE + "/99999999", Map.of("name", "Ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------------------------------------------------------------
    // P0-6/P1-5：删除级联逻辑删 + 软删后 code 可复用
    // ---------------------------------------------------------------

    @Test
    void should_cascadeLogicalDeleteAndReuseCode_when_delete() throws Exception {
        // Given：提供商 + 一条模型配置
        postJson(BASE, createBody("it-delete", "IT Delete Provider",
                "https://delete.example.com", "sk-test-delete-001"))
                .andExpect(status().isOk());
        Long id = providerId("it-delete");
        jdbcTemplate.update(
                "INSERT INTO t_model_config (provider_id, name, model_id, model_type, status) "
                        + "VALUES (?, 'IT 模型', 'it-model', 1, 1)", id);

        // When
        deleteJson(BASE + "/{0}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Then：提供商 / 模型配置 / 健康行全部逻辑删除
        assertThat(deletedFlag("t_provider", id)).isEqualTo(1);
        assertThat(deletedFlag("t_model_config",
                "WHERE provider_id = " + id)).isEqualTo(1);
        assertThat(deletedFlag("t_provider_health",
                "WHERE provider_id = " + id)).isEqualTo(1);
        // 详情立即 404
        getJson(BASE + "/{0}", id)
                .andExpect(status().isNotFound());

        // When：用同一 code 重新创建（应用层唯一性只看 deleted=0）
        postJson(BASE, createBody("it-delete", "IT Delete Reborn",
                "https://reborn.example.com", "sk-test-delete-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authConfig.apiKey").value("******"));
        Long rebornId = providerId("it-delete");

        // Then：拿到全新自增 id；旧 id 再次删除仍是 404
        assertThat(rebornId).isNotEqualTo(id);
        deleteJson(BASE + "/{0}", id).andExpect(status().isNotFound());
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private int deletedFlag(String table, Long id) {
        return deletedFlag(table, "WHERE id = " + id);
    }

    private int deletedFlag(String table, String where) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT deleted FROM " + table + " " + where, Integer.class);
        return value == null ? -1 : value;
    }
}
