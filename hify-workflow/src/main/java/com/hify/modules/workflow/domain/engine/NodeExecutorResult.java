package com.hify.modules.workflow.domain.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点执行结果。
 *
 * @param outputs          要写入变量池的输出（引擎按 {@code nodeId.key} 写入）
 * @param recordedInputs   落节点执行记录的实际输入（如渲染后的 prompt）
 * @param selectedHandle   CONDITION 节点命中的分支 id（未命中任何分支为 null，由引擎尝试 default 出线）
 * @param promptTokens     LLM 输入 token，其他节点为 null
 * @param completionTokens LLM 输出 token，其他节点为 null
 */
public record NodeExecutorResult(
        Map<String, Object> outputs,
        Map<String, Object> recordedInputs,
        String selectedHandle,
        Integer promptTokens,
        Integer completionTokens
) {

    public static NodeExecutorResult plain(Map<String, Object> outputs, Map<String, Object> recordedInputs) {
        return new NodeExecutorResult(safe(outputs), safe(recordedInputs), null, null, null);
    }

    public static NodeExecutorResult condition(String selectedHandle,
                                               Map<String, Object> outputs,
                                               Map<String, Object> recordedInputs) {
        return new NodeExecutorResult(safe(outputs), safe(recordedInputs), selectedHandle, null, null);
    }

    public static NodeExecutorResult llm(Map<String, Object> outputs,
                                         Map<String, Object> recordedInputs,
                                         Integer promptTokens,
                                         Integer completionTokens) {
        return new NodeExecutorResult(safe(outputs), safe(recordedInputs), null, promptTokens, completionTokens);
    }

    private static Map<String, Object> safe(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }
}
