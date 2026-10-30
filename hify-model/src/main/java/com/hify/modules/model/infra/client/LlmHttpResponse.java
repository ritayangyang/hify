package com.hify.modules.model.infra.client;

/**
 * LLM HTTP 响应（仅保留连通性测试需要的字段）。
 *
 * @param status HTTP 状态码
 * @param body   响应体
 */
public record LlmHttpResponse(int status, String body) {
}
