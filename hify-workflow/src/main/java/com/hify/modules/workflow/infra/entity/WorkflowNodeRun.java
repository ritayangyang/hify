package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import com.hify.modules.workflow.api.enums.RunStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 节点执行记录，对应表 t_workflow_node_run。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_node_run", autoResultMap = true)
public class WorkflowNodeRun extends BaseEntity {

    private Long runId;

    private String nodeId;

    private String nodeType;

    private RunStatus status;

    @TableField(value = "inputs", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputs;

    @TableField(value = "outputs", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputs;

    private String errorMessage;

    private Integer elapsedMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private LocalDateTime finishedAt;
}
