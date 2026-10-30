package com.hify.modules.workflow.domain.repository;

import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.enums.RunStatus;

import java.util.Map;

/**
 * 工作流执行记录仓储端口。
 * 执行过程跨多次 LLM 调用，RUNNING 插入与终态更新各自独立提交，不套大事务。
 */
public interface WorkflowRunRepository {

    /** 插入一条 RUNNING 记录，回填自增 id */
    Long insertRunning(Long workflowId, Map<String, Object> inputs);

    /**
     * CAS 结束执行：仅当当前状态为 RUNNING 时更新，返回是否命中。
     * 超时兜底线程与引擎线程并发收尾时，先到的一方生效。
     */
    boolean finish(Long runId, RunStatus status, String output, String errorMessage,
                   int elapsedMs, Integer totalTokens);

    WorkflowRunDto findById(Long runId);

    /**
     * @param workflowId 为 null 时查全部工作流
     */
    PageResponse<WorkflowRunDto> page(Long workflowId, long page, long size);
}
