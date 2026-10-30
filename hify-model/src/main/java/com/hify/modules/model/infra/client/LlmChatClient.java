package com.hify.modules.model.infra.client;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM 非流式对话的 HTTP 客户端。
 *
 * <p>与 {@link LlmHttpClient}（连通性测试，10s 超时）分开：对话单次读取可能很慢，
 * 按项目超时层次 connect=5s、read=120s、write=30s。
 */
@Component
public class LlmChatClient {

    public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    public LlmChatClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    private final OkHttpClient httpClient;

    /**
     * 发起 POST JSON 请求。
     *
     * @param url      完整地址
     * @param headers  请求头（鉴权等，可为 null）
     * @param jsonBody 请求体 JSON 文本
     */
    public LlmHttpResponse postJson(String url, Map<String, String> headers, String jsonBody) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            return new LlmHttpResponse(response.code(), body == null ? "" : body.string());
        }
    }
}
