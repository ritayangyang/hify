package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流连线，对应表 t_workflow_edge。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_edge")
public class WorkflowEdge extends BaseEntity {

    /** 所属工作流 t_workflow.id */
    private Long workflowId;

    /** 图内连线业务 id（前端生成，可空） */
    private String edgeId;

    /** 起点节点业务 id */
    private String sourceNodeId;

    /** 条件分支 id（b1/default）；普通节点唯一出线为 null */
    private String sourceHandle;

    /** 终点节点业务 id */
    private String targetNodeId;
}
