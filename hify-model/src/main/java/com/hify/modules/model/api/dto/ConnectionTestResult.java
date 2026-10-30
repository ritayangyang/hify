package com.hify.modules.model.api.dto;

/**
 * 连通性测试统一结果。
 *
 * @param success      是否连通
 * @param latencyMs    本次测试耗时（毫秒）
 * @param modelCount   拉取到的模型数量；失败时为 0
 * @param errorMessage 失败原因；成功时为 null
 */
public record ConnectionTestResult(
        boolean success,
        long latencyMs,
        int modelCount,
        String errorMessage
) {

    public static ConnectionTestResult ok(long latencyMs, int modelCount) {
        return new ConnectionTestResult(true, latencyMs, modelCount, null);
    }

    public static ConnectionTestResult fail(long latencyMs, String errorMessage) {
        return new ConnectionTestResult(false, latencyMs, 0, errorMessage);
    }
}
