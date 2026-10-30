package com.hify.modules.workflow.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeRunDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.enums.RunStatus;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRunRepository;
import com.hify.modules.workflow.infra.entity.WorkflowNodeRun;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 节点执行记录仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class WorkflowNodeRunRepositoryImpl implements WorkflowNodeRunRepository {

    private static final int ERROR_MAX_LENGTH = 500;

    private final WorkflowNodeRunMapper workflowNodeRunMapper;

    @Override
    public Long insertRunning(Long runId, WorkflowNodeDto node) {
        WorkflowNodeRun entity = new WorkflowNodeRun();
        entity.setRunId(runId);
        entity.setNodeId(node.id());
        entity.setNodeType(node.type().name());
        entity.setStatus(RunStatus.RUNNING);
        entity.setElapsedMs(0);
        workflowNodeRunMapper.insert(entity);
        return entity.getId();
    }

    /*
     * 注意：终态更新必须走 entity（mapper.update(entity, whereWrapper)），
     * 不能用 LambdaUpdateWrapper.set(...)——wrapper 的 set 不走字段上声明的
     * JacksonTypeHandler，Map 会以 binary 字符集直送 MySQL JSON 列，报
     * "Cannot create a JSON value from a string with CHARACTER SET 'binary'"。
     * CAS（仅 RUNNING 可收尾）放在 where 条件里；null 字段按 NOT_NULL 策略自动忽略。
     */

    @Override
    public boolean markSuccess(Long nodeRunId, Map<String, Object> inputs, Map<String, Object> outputs,
                               int elapsedMs, Integer promptTokens, Integer completionTokens) {
        WorkflowNodeRun entity = new WorkflowNodeRun();
        entity.setStatus(RunStatus.SUCCEEDED);
        entity.setInputs(inputs);
        entity.setOutputs(outputs);
        entity.setElapsedMs(elapsedMs);
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setFinishedAt(LocalDateTime.now());
        return workflowNodeRunMapper.update(entity, runningCasWrapper(nodeRunId)) > 0;
    }

    @Override
    public boolean markFailed(Long nodeRunId, String errorMessage, int elapsedMs) {
        WorkflowNodeRun entity = new WorkflowNodeRun();
        entity.setStatus(RunStatus.FAILED);
        entity.setErrorMessage(truncate(errorMessage));
        entity.setElapsedMs(elapsedMs);
        entity.setFinishedAt(LocalDateTime.now());
        return workflowNodeRunMapper.update(entity, runningCasWrapper(nodeRunId)) > 0;
    }

    private LambdaUpdateWrapper<WorkflowNodeRun> runningCasWrapper(Long nodeRunId) {
        return new LambdaUpdateWrapper<WorkflowNodeRun>()
                .eq(WorkflowNodeRun::getId, nodeRunId)
                .eq(WorkflowNodeRun::getStatus, RunStatus.RUNNING);
    }

    @Override
    public List<WorkflowNodeRunDto> listByRunId(Long runId) {
        return workflowNodeRunMapper.selectList(
                        new LambdaQueryWrapper<WorkflowNodeRun>()
                                .eq(WorkflowNodeRun::getRunId, runId)
                                .orderByAsc(WorkflowNodeRun::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private WorkflowNodeRunDto toDto(WorkflowNodeRun entity) {
        NodeType nodeType;
        try {
            nodeType = NodeType.valueOf(entity.getNodeType());
        } catch (IllegalArgumentException e) {
            nodeType = null;
        }
        return new WorkflowNodeRunDto(
                entity.getId(),
                entity.getRunId(),
                entity.getNodeId(),
                nodeType,
                entity.getStatus(),
                entity.getInputs(),
                entity.getOutputs(),
                entity.getErrorMessage(),
                entity.getElapsedMs(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getCreatedAt(),
                entity.getFinishedAt());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MAX_LENGTH ? message : message.substring(0, ERROR_MAX_LENGTH);
    }
}
