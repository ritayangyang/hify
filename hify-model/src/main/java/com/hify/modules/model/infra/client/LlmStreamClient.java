package com.hify.modules.model.infra.client;

import lombok.Getter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * LLM 流式对话 HTTP 客户端（SSE）。
 *
 * <p>与 {@link LlmChatClient} 分开：SSE 长连接不能有读超时（readTimeout=0），
 * connect=5s、write=30s，总等待由调用方/容器超时兜底。
 * 方法同步阻塞到流结束；每帧 {@code data:} 内容回调一次 onEvent。
 */
@Component
public class LlmStreamClient {

    public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** 单次流式对话的等待上限（SSE 建连后的整体兜底，略小于对话层 emitter 的 200s） */
    private static final long STREAM_AWAIT_SECONDS = 180;

    private final OkHttpClient httpClient;

    public LlmStreamClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                // SSE 不能有读超时：两次 delta 之间可能长时间无数据
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ofSeconds(30))
                .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * POST JSON 并消费 SSE 流。
     *
     * @param onEvent 每帧 data 内容（如 [DONE] 或一段 JSON）；回调抛 RuntimeException 时
     *                立即取消上游调用并把该异常原样抛出（用于客户端断开即停）
     * @throws IOException           网络错误
     * @throws HttpStatusException   建连响应非 2xx（携带状态码与错误体，供上层转换业务错误）
     * @throws IllegalStateException 等待流结束超时
     */
    public void postStream(String url, Map<String, String> headers, String jsonBody,
                           Consumer<String> onEvent) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE));
        if (headers != null) {
            headers.forEach(builder::header);
        }

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        AtomicReference<RuntimeException> callbackAbortRef = new AtomicReference<>();

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    onEvent.accept(data);
                } catch (RuntimeException e) {
                    // 下游（如 SSE 转发）要求中止：取消上游并保留原异常
                    callbackAbortRef.compareAndSet(null, e);
                    eventSource.cancel();
                    finished.countDown();
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                finished.countDown();
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                if (response != null && (response.code() < 200 || response.code() >= 300)) {
                    String body = "";
                    try (ResponseBody rb = response.body()) {
                        body = rb == null ? "" : rb.string();
                    } catch (IOException ignored) {
                        // 保留空错误体
                    }
                    failureRef.compareAndSet(null, new HttpStatusException(response.code(), body));
                } else if (t != null) {
                    failureRef.compareAndSet(null, t);
                }
                finished.countDown();
            }
        };

        EventSource source = EventSources.createFactory(httpClient)
                .newEventSource(builder.build(), listener);

        boolean done;
        try {
            done = finished.await(STREAM_AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            source.cancel();
            Thread.currentThread().interrupt();
            throw new IOException("流式对话被中断", e);
        }

        RuntimeException callbackAbort = callbackAbortRef.get();
        if (callbackAbort != null) {
            throw callbackAbort;
        }
        Throwable failure = failureRef.get();
        if (failure != null) {
            if (failure instanceof HttpStatusException httpStatusException) {
                throw httpStatusException;
            }
            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("LLM 流式调用失败: " + failure.getMessage(), failure);
        }
        if (!done) {
            source.cancel();
            throw new IllegalStateException("流式对话等待超时（" + STREAM_AWAIT_SECONDS + "s）");
        }
    }

    /** 上游返回非 2xx（HTTP 状态码 + 错误体） */
    @Getter
    public static class HttpStatusException extends IOException {
        private final int status;

        public HttpStatusException(int status, String body) {
            super(body);
            this.status = status;
        }
    }
}
