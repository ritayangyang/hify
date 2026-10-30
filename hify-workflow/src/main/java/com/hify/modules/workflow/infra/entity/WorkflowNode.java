package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流节点，对应表 t_workflow_node。
 * 异构配置统一存 config_json，类型安全的解析在 domain 层 NodeConfigParser 完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_node", autoResultMap = true)
public class WorkflowNode extends BaseEntity {

    /** 所属工作流 t_workflow.id */
    private Long workflowId;

    /** 图内节点业务 id（start/n2…） */
    private String nodeId;

    /** 节点类型：START/LLM/CONDITION/TOOL/KNOWLEDGE/END */
    private String nodeType;

    /** 按 node_type 多态解析的节点配置（不含 type 字段，类型独立成列） */
    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode configJson;

    /** 画布坐标 X（前端使用，后端不解释） */
    private Double posX;

    /** 画布坐标 Y（前端使用，后端不解释） */
    private Double posY;
}
