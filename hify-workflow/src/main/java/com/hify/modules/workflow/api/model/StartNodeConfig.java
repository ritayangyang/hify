package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;

import java.util.List;

/**
 * 开始节点配置：声明工作流触发时需要的入参字段名，如 ["orderId", "userMessage"]。
 * 运行时这些字段进入小黑板，其他节点用 {@code {{start.orderId}}} 引用。
 *
 * @param inputs 入参字段名列表，可为空
 */
public record StartNodeConfig(List<String> inputs) implements NodeConfig {

    public StartNodeConfig {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    @Override
    public NodeType type() {
        return NodeType.START;
    }
}
