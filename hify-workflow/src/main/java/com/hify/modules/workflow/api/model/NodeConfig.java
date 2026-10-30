package com.hify.modules.workflow.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hify.modules.workflow.api.enums.NodeType;

/**
 * 节点配置的多态根类型。
 *
 * <p>类型标记复用节点外壳上的 {@code type} 字段（EXISTING_PROPERTY）：
 * 解析时由 NodeConfigParser 把 type 并入 config JSON，Jackson 据此选择子类 record；
 * 落库时 type 独立成 {@code node_type} 列，config_json 内不重复存。
 *
 * <p>sealed  permits 清单固定全部节点类型，未来新增节点时执行器侧 switch
 * 不补全分支会直接编译失败。
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartNodeConfig.class, name = "START"),
        @JsonSubTypes.Type(value = LlmNodeConfig.class, name = "LLM"),
        @JsonSubTypes.Type(value = ConditionNodeConfig.class, name = "CONDITION"),
        @JsonSubTypes.Type(value = ToolNodeConfig.class, name = "TOOL"),
        @JsonSubTypes.Type(value = KnowledgeNodeConfig.class, name = "KNOWLEDGE"),
        @JsonSubTypes.Type(value = EndNodeConfig.class, name = "END"),
})
public sealed interface NodeConfig
        permits StartNodeConfig, LlmNodeConfig, ConditionNodeConfig,
                ToolNodeConfig, KnowledgeNodeConfig, EndNodeConfig {

    /** 节点类型，与 {@code t_workflow_node.node_type} 一致 */
    NodeType type();
}
