package com.hify.modules.model.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import com.hify.modules.model.api.enums.ProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 模型提供商，对应表 t_provider。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_provider", autoResultMap = true)
public class Provider extends BaseEntity {

    /** 提供商编码：openai/claude/gemini/ollama，用于 fallback 路由 */
    private String code;

    /** 提供商类型（协议分发）：OPENAI / OPENAI_COMPATIBLE / ANTHROPIC / OLLAMA */
    private ProviderType type;

    /** 提供商展示名称 */
    private String name;

    /** API 基础地址 */
    private String baseUrl;

    /** 认证配置，如 {"apiKey":"sk-xxx"} */
    @TableField(value = "auth_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /** 状态：0 禁用，1 启用 */
    private Integer status;
}
