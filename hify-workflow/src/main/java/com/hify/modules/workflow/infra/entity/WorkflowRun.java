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
 * 工作流执行记录，对应表 t_workflow_run。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_run", autoResultMap = true)
public class WorkflowRun extends BaseEntity {

    private Long workflowId;

    /** RUNNING / SUCCEEDED / FAILED，存字符串 code */
    private RunStatus status;

    @TableField(value = "inputs", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputs;

    private String output;

    private String errorMessage;

    private Integer elapsedMs;

    private Integer totalTokens;

    private LocalDateTime finishedAt;
}
