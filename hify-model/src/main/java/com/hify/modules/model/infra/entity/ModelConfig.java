package com.hify.modules.model.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 模型配置，对应表 t_model_config。
 * 展示名 {@link #name} 与真实模型标识 {@link #modelId} 分开存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_config", autoResultMap = true)
public class ModelConfig extends BaseEntity {

    /** 所属提供商 t_provider.id */
    private Long providerId;

    /** 模型展示名称，如：GPT-4o 生产 */
    private String name;

    /** 真实模型标识，如：gpt-4o / claude-3-5-sonnet */
    private String modelId;

    /** 模型类型：1 chat 对话，2 embedding */
    private Integer modelType;

    /** 状态：0 禁用，1 启用 */
    private Integer status;

    /** 额外参数，如 temperature/top_p/max_tokens 等 */
    @TableField(value = "extra_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;
}
