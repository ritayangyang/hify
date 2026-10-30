package com.hify.modules.workflow.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.domain.repository.WorkflowEdgeRepository;
import com.hify.modules.workflow.infra.entity.WorkflowEdge;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流连线仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class WorkflowEdgeRepositoryImpl implements WorkflowEdgeRepository {

    private final WorkflowEdgeMapper workflowEdgeMapper;

    @Override
    public void insertBatch(Long workflowId, List<WorkflowEdgeDto> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        for (WorkflowEdgeDto edge : edges) {
            WorkflowEdge entity = new WorkflowEdge();
            entity.setWorkflowId(workflowId);
            entity.setEdgeId(edge.id());
            entity.setSourceNodeId(edge.source());
            entity.setSourceHandle(edge.sourceHandle());
            entity.setTargetNodeId(edge.target());
            workflowEdgeMapper.insert(entity);
        }
    }

    @Override
    public List<WorkflowEdgeDto> listByWorkflowId(Long workflowId) {
        return workflowEdgeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowEdge>()
                                .eq(WorkflowEdge::getWorkflowId, workflowId)
                                .orderByAsc(WorkflowEdge::getId))
                .stream()
                .map(entity -> new WorkflowEdgeDto(
                        entity.getEdgeId(),
                        entity.getSourceNodeId(),
                        entity.getSourceHandle(),
                        entity.getTargetNodeId()))
                .toList();
    }

    @Override
    public int deleteByWorkflowId(Long workflowId) {
        return workflowEdgeMapper.delete(
                new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, workflowId));
    }

    @Override
    public Map<Long, Integer> countByWorkflowIds(Collection<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return workflowEdgeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowEdge>()
                                .select(WorkflowEdge::getWorkflowId)
                                .in(WorkflowEdge::getWorkflowId, workflowIds))
                .stream()
                .collect(Collectors.groupingBy(WorkflowEdge::getWorkflowId,
                        Collectors.summingInt(edge -> 1)));
    }
}
