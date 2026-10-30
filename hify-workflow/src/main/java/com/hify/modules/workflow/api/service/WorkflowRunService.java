package com.hify.modules.workflow.api.service;

import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunStartCmd;

/**
 * 工作流执行服务（其他模块只能依赖本接口）。
 * 同步执行：返回时执行已结束（SUCCEEDED）或抛出 BizException（FAILED）。
 */
public interface WorkflowRunService {

    /**
     * 触发一次执行。
     *
     * @param workflowId 工作流 id
     * @param cmd        触发入参
     * @return 执行详情（含每个节点的执行记录）
     */
    WorkflowRunDetailDto start(Long workflowId, WorkflowRunStartCmd cmd);

    /**
     * 查询执行详情。
     */
    WorkflowRunDetailDto getDetail(Long runId);

    /**
     * 分页查询执行记录（可按工作流过滤）。
     *
     * @param workflowId 为 null 时查全部工作流
     */
    PageResponse<WorkflowRunDto> page(Long workflowId, long page, long size);
}
