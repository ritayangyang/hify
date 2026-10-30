package com.hify.common.config;

import io.micrometer.tracing.CurrentTraceContext;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 在任务提交时快照当前 trace 上下文、工作线程执行前恢复的线程池。
 * <p>请求线程由 micrometer-tracing(OTel) 写入 traceId/spanId 并写入 SLF4J MDC，
 * SSE/工作流任务提交到 llm-pool/llm-stream 后属于另一个线程。只拷贝 MDC 不够：
 * 工作线程上后续的 OTel 嵌套 scope（如 Hikari 上下文包装）关闭时会按该线程的
 * OTel Context 重置 MDC，把拷贝来的 traceId 清掉。必须让工作线程 attach 同一个
 * TraceContext，traceId/spanId/关联字段的 MDC 写入与恢复均由 tracing 监听器统一管理。
 */
public class TraceContextPropagatingThreadPoolExecutor extends ThreadPoolExecutor {

    private final CurrentTraceContext currentTraceContext;

    public TraceContextPropagatingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                                     TimeUnit unit, LinkedBlockingQueue<Runnable> workQueue,
                                                     ThreadFactory threadFactory,
                                                     RejectedExecutionHandler handler,
                                                     CurrentTraceContext currentTraceContext) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.currentTraceContext = currentTraceContext;
    }

    @Override
    public void execute(Runnable task) {
        // wrap 在提交方线程捕获当前 TraceContext，工作线程执行时重新 attach
        super.execute(currentTraceContext.wrap(task));
    }
}
