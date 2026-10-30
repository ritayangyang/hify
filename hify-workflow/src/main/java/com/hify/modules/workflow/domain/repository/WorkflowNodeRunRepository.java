package com.hify.modules.workflow.domain.repository;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeRunDto;

import java.util.List;
import java.util.Map;

/**
 * 节点执行记录仓储端口。
 */
public interface WorkflowNodeRunRepository {

    /** 插入一条节点 RUNNING 记录，回填 id */
    Long insertRunning(Long runId, WorkflowNodeDto node);

    /** 标记节点成功（写入实际输入输出与 token）；CAS：仅 RUNNING 可更新 */
    boolean markSuccess(Long nodeRunId, Map<String, Object> inputs, Map<String, Object> outputs,
                        int elapsedMs, Integer promptTokens, Integer completionTokens);

    /** 标记节点失败；CAS：仅 RUNNING 可更新 */
    boolean markFailed(Long nodeRunId, String errorMessage, int elapsedMs);

    /** 按执行记录查全部节点记录，id 升序即实际执行顺序 */
    List<WorkflowNodeRunDto> listByRunId(Long runId);
}
