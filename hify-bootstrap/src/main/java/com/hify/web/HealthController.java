package com.hify.web;

import com.hify.common.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用健康检查（版本化 API 路径）。
 * <p>固定返回成功，不检查数据库等任何依赖：本地 curl 探活、Docker HEALTHCHECK、
 * K8s liveness/readiness 探针都只依赖 HTTP 200 状态码，不解析响应体。
 * 响应遵循 Hify 统一信封（成功 code=200，非 0；前端拦截器与全系统一致按 200 判定）。
 */
@RestController("apiHealthController")
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
