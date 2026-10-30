package com.hify.modules.workflow.api.dto;

import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.enums.RunStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 节点执行记录（id 升序即实际执行顺序）。
 *
 * @param inputs           节点实际输入，如渲染后的 prompt
 * @param outputs          节点实际输出（写入变量池的键值）
 * @param promptTokens     LLM 输入 token（其他节点为 null）
 * @param completionTokens LLM 输出 token（其他节点为 null）
 */
public record WorkflowNodeRunDto(
        Long id,
        Long runId,
        String nodeId,
        NodeType nodeType,
        RunStatus status,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String errorMessage,
        Integer elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
