package com.hify.modules.model.infra.client;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM 外部调用的统一 HTTP 客户端。
 * 当前服务于连通性测试：读写/总超时 10 秒，连接超时 5 秒。
 */
@Component
public class LlmHttpClient {

    /** 连通性测试超时：10 秒 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OkHttpClient httpClient;

    public LlmHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(TIMEOUT)
                .writeTimeout(TIMEOUT)
                .callTimeout(TIMEOUT)
                .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 发起 GET 请求。
     *
     * @param url     完整地址
     * @param headers 请求头（可为 null）
     */
    public LlmHttpResponse get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            return new LlmHttpResponse(response.code(), body == null ? "" : body.string());
        }
    }
}
