package com.hify.it.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.HifyApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 集成测试基类：Controller → Service → Mapper → 真实 MySQL(hify_test) 全链路。
 * <ul>
 *   <li>只放在 hify-bootstrap（唯一装配全部模块的模块）；</li>
 *   <li>同步链路用例统一 {@link Transactional}，测试结束自动回滚，不污染 hify_test；</li>
 *   <li>外部 HTTP（LLM/提供商端点）由各用例自行用 MockWebServer 替掉。</li>
 * </ul>
 */
@SpringBootTest(classes = HifyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 对象 → JSON 字符串，测试对象序列化失败属于测试代码问题，直接转非受检异常 */
    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResultActions getJson(String uriTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(get(uriTemplate, uriVars)
                .accept(MediaType.APPLICATION_JSON));
    }

    protected ResultActions postJson(String uri, Object body) throws Exception {
        return mockMvc.perform(post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    /** 无请求体 POST（如 /{id}/test-connection） */
    protected ResultActions postNoBody(String uriTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(post(uriTemplate, uriVars)
                .accept(MediaType.APPLICATION_JSON));
    }

    /** 发送原始字符串请求体，用于构造坏 JSON 等非法报文 */
    protected ResultActions postRaw(String uri, String rawBody) throws Exception {
        return mockMvc.perform(post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawBody));
    }

    protected ResultActions putJson(String uri, Object body) throws Exception {
        return mockMvc.perform(put(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    protected ResultActions deleteJson(String uriTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(delete(uriTemplate, uriVars)
                .accept(MediaType.APPLICATION_JSON));
    }
}
