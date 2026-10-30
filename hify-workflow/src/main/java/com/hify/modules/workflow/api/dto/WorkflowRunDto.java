package com.hify.modules.workflow.api.dto;

import com.hify.modules.workflow.api.enums.RunStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流执行记录。
 *
 * @param id           执行记录 id
 * @param workflowId   工作流 id
 * @param status       执行状态
 * @param inputs       触发入参
 * @param output       最终输出（FAILED 时为 null）
 * @param errorMessage 失败原因
 * @param elapsedMs    总耗时毫秒
 * @param totalTokens  LLM 节点 token 合计（无 LLM 节点时为 null）
 * @param createdAt    开始时间
 * @param finishedAt   结束时间（RUNNING 时为 null）
 */
public record WorkflowRunDto(
        Long id,
        Long workflowId,
        RunStatus status,
        Map<String, Object> inputs,
        String output,
        String errorMessage,
        Integer elapsedMs,
        Integer totalTokens,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
