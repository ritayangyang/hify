package com.hify.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口，供负载均衡 / K8s 探针使用。
 * 固定返回 success，不依赖数据库等任何中间件。
 */
@RestController
public class HealthController {

    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    public String health() {
        return "success";
    }
}
