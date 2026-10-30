package com.hify.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.tracing.CurrentTraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置。
 * 显式创建（禁止 Executors 无界队列），守护线程避免阻止 JVM 退出。
 * 两个池均包装 micrometer TraceContext：任务跨线程执行时同一条 traceId 不断链。
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 非流式 LLM 调用 / 工作流执行：IO 密集型任务。
     * 队列满时 CallerRunsPolicy：由调用方线程执行，形成背压，不丢任务。
     */
    @Bean("llmExecutor")
    public ThreadPoolExecutor llmExecutor(CurrentTraceContext currentTraceContext) {
        return new TraceContextPropagatingThreadPoolExecutor(
                20,
                50,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadFactoryBuilder().setNameFormat("llm-pool-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy(),
                currentTraceContext);
    }

    /**
     * 流式 SSE 对话：每个任务占一条长连接线程。
     * 队列满直接 AbortPolicy 拒绝，由上层捕获后通过 SSE 推错误事件（不占用 Tomcat 线程排队）。
     */
    @Bean("llmStreamExecutor")
    public ThreadPoolExecutor llmStreamExecutor(CurrentTraceContext currentTraceContext) {
        return new TraceContextPropagatingThreadPoolExecutor(
                30,
                80,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactoryBuilder().setNameFormat("llm-stream-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.AbortPolicy(),
                currentTraceContext);
    }
}
