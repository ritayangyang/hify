package com.hify.modules.workflow.domain.repository;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点仓储端口。
 * 节点随工作流整体维护：批量插入、按工作流逻辑删除，不做单行更新。
 */
public interface WorkflowNodeRepository {

    /** 批量插入节点（按列表顺序，落库后顺序即 id 升序） */
    void insertBatch(Long workflowId, List<WorkflowNodeDto> nodes);

    /** 按工作流查询全部节点，id 升序（即保存时的提交顺序） */
    List<WorkflowNodeDto> listByWorkflowId(Long workflowId);

    /** 逻辑删除该工作流下的全部节点 */
    int deleteByWorkflowId(Long workflowId);

    /** 批量统计多个工作流的节点数（列表装配用，内存分组避免 N+1） */
    Map<Long, Integer> countByWorkflowIds(Collection<Long> workflowIds);
}
