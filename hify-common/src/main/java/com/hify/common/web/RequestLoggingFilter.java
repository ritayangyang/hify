package com.hify.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * HTTP 请求关键节点日志：请求进入 + 响应完成（状态码、耗时）。
 * traceId 由 micrometer-tracing 的过滤器写入 MDC（其优先级更高），本过滤器排在其后。
 * 健康检查/actuator 端点不记录，避免探针噪音淹没业务日志。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("HTTP_ACCESS");

    private static final Set<String> IGNORED_PATHS = Set.of(
            "/health", "/api/v1/health");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return IGNORED_PATHS.contains(request.getRequestURI())
                || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        log.info("HTTP 请求进入 method={} uri={}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();
            if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                log.warn("HTTP 请求完成 method={} uri={} status={} latencyMs={}",
                        request.getMethod(), request.getRequestURI(), status, latencyMs);
            } else {
                log.info("HTTP 请求完成 method={} uri={} status={} latencyMs={}",
                        request.getMethod(), request.getRequestURI(), status, latencyMs);
            }
        }
    }
}
