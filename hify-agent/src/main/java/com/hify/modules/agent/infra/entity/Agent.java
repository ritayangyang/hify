package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Agent 配置，对应表 t_agent。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent", autoResultMap = true)
public class Agent extends BaseEntity {

    /** Agent 名称，活跃记录范围内应用层唯一 */
    private String name;

    /** Agent 简介 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 绑定模型 t_model_config.id（不建物理外键） */
    private Long modelConfigId;

    /**
     * 绑定工作流 t_workflow.id；NULL 表示普通对话（不建物理外键）。
     * updateStrategy=ALWAYS：解绑时显式置 NULL 必须写入 UPDATE SET，
     * 默认 NOT_NULL 策略会把 null 字段从 SET 中去掉导致解绑失效。
     */
    @TableField(value = "workflow_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long workflowId;

    /** 生成参数覆盖：temperature/top_p/max_tokens */
    @TableField(value = "model_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modelParams;

    /** 状态：0 禁用，1 启用 */
    private Integer status;
}
