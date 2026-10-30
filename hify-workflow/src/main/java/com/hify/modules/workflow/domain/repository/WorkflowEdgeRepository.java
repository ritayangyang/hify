package com.hify.modules.workflow.domain.repository;

import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工作流连线仓储端口。
 */
public interface WorkflowEdgeRepository {

    /** 批量插入连线（按列表顺序） */
    void insertBatch(Long workflowId, List<WorkflowEdgeDto> edges);

    /** 按工作流查询全部连线，id 升序 */
    List<WorkflowEdgeDto> listByWorkflowId(Long workflowId);

    /** 逻辑删除该工作流下的全部连线 */
    int deleteByWorkflowId(Long workflowId);

    /** 批量统计多个工作流的连线数（列表装配用，内存分组避免 N+1） */
    Map<Long, Integer> countByWorkflowIds(Collection<Long> workflowIds);
}
