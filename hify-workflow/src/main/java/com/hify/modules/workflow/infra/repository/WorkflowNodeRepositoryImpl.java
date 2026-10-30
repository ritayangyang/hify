package com.hify.modules.workflow.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.NodeConfig;
import com.hify.modules.workflow.api.model.NodePosition;
import com.hify.modules.workflow.domain.NodeConfigParser;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRepository;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流节点仓储实现。
 * config_json ↔ NodeConfig 的多态转换收口在本层（经 NodeConfigParser）。
 */
@Repository
@RequiredArgsConstructor
public class WorkflowNodeRepositoryImpl implements WorkflowNodeRepository {

    private final WorkflowNodeMapper workflowNodeMapper;
    private final NodeConfigParser nodeConfigParser;

    @Override
    public void insertBatch(Long workflowId, List<WorkflowNodeDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (WorkflowNodeDto node : nodes) {
            WorkflowNode entity = new WorkflowNode();
            entity.setWorkflowId(workflowId);
            entity.setNodeId(node.id());
            entity.setNodeType(node.type().name());
            entity.setConfigJson(nodeConfigParser.toTree(node.config()));
            if (node.position() != null) {
                entity.setPosX(node.position().x());
                entity.setPosY(node.position().y());
            }
            workflowNodeMapper.insert(entity);
        }
    }

    @Override
    public List<WorkflowNodeDto> listByWorkflowId(Long workflowId) {
        return workflowNodeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowNode>()
                                .eq(WorkflowNode::getWorkflowId, workflowId)
                                .orderByAsc(WorkflowNode::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public int deleteByWorkflowId(Long workflowId) {
        return workflowNodeMapper.delete(
                new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, workflowId));
    }

    @Override
    public Map<Long, Integer> countByWorkflowIds(Collection<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 单页最多 50 个工作流，只取 workflow_id 列内存分组，免去自定义 XML
        return workflowNodeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowNode>()
                                .select(WorkflowNode::getWorkflowId)
                                .in(WorkflowNode::getWorkflowId, workflowIds))
                .stream()
                .collect(Collectors.groupingBy(WorkflowNode::getWorkflowId,
                        Collectors.summingInt(node -> 1)));
    }

    private WorkflowNodeDto toDto(WorkflowNode entity) {
        NodeType type = NodeType.valueOf(entity.getNodeType());
        NodeConfig config = nodeConfigParser.fromStorage(entity.getNodeId(), type, entity.getConfigJson());
        NodePosition position = (entity.getPosX() == null && entity.getPosY() == null)
                ? null
                : new NodePosition(entity.getPosX(), entity.getPosY());
        return new WorkflowNodeDto(entity.getNodeId(), type, config, position);
    }
}
