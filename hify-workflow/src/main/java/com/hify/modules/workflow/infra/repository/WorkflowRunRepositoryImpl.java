package com.hify.modules.workflow.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.enums.RunStatus;
import com.hify.modules.workflow.domain.repository.WorkflowRunRepository;
import com.hify.modules.workflow.infra.entity.WorkflowRun;
import com.hify.modules.workflow.infra.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行记录仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class WorkflowRunRepositoryImpl implements WorkflowRunRepository {

    /** error_message 列长度 */
    private static final int ERROR_MAX_LENGTH = 500;

    private final WorkflowRunMapper workflowRunMapper;

    @Override
    public Long insertRunning(Long workflowId, Map<String, Object> inputs) {
        WorkflowRun entity = new WorkflowRun();
        entity.setWorkflowId(workflowId);
        entity.setStatus(RunStatus.RUNNING);
        entity.setInputs(inputs);
        entity.setElapsedMs(0);
        workflowRunMapper.insert(entity);
        return entity.getId();
    }

    /*
     * 终态更新走 entity 而非 LambdaUpdateWrapper.set(...)：后者不会应用字段上的
     * JacksonTypeHandler（inputs 是 MySQL JSON 列）。CAS 条件（仅 RUNNING 可收尾）
     * 放在 where 中；output/errorMessage/totalTokens 为 null 时按 NOT_NULL 策略忽略。
     */

    @Override
    public boolean finish(Long runId, RunStatus status, String output, String errorMessage,
                          int elapsedMs, Integer totalTokens) {
        WorkflowRun entity = new WorkflowRun();
        entity.setStatus(status);
        entity.setOutput(output);
        entity.setErrorMessage(truncate(errorMessage));
        entity.setElapsedMs(elapsedMs);
        entity.setTotalTokens(totalTokens);
        entity.setFinishedAt(LocalDateTime.now());
        LambdaUpdateWrapper<WorkflowRun> wrapper = new LambdaUpdateWrapper<WorkflowRun>()
                .eq(WorkflowRun::getId, runId)
                .eq(WorkflowRun::getStatus, RunStatus.RUNNING);
        return workflowRunMapper.update(entity, wrapper) > 0;
    }

    @Override
    public WorkflowRunDto findById(Long runId) {
        WorkflowRun entity = workflowRunMapper.selectById(runId);
        return entity == null ? null : toDto(entity);
    }

    @Override
    public PageResponse<WorkflowRunDto> page(Long workflowId, long page, long size) {
        LambdaQueryWrapper<WorkflowRun> wrapper = new LambdaQueryWrapper<WorkflowRun>()
                .eq(workflowId != null, WorkflowRun::getWorkflowId, workflowId)
                .orderByDesc(WorkflowRun::getId);
        Page<WorkflowRun> result = workflowRunMapper.selectPage(new Page<>(page, size), wrapper);
        List<WorkflowRunDto> records = result.getRecords().stream().map(this::toDto).toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private WorkflowRunDto toDto(WorkflowRun entity) {
        return new WorkflowRunDto(
                entity.getId(),
                entity.getWorkflowId(),
                entity.getStatus(),
                entity.getInputs(),
                entity.getOutput(),
                entity.getErrorMessage(),
                entity.getElapsedMs(),
                entity.getTotalTokens(),
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
