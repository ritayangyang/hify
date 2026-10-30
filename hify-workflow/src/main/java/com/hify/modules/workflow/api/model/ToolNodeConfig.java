package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 工具调用节点配置（mcp 模块落地后生效，本期先存配置并做结构校验）。
 *
 * @param mcpToolId 绑定 MCP 工具 id
 * @param args      入参数名到实参的映射，实参可写 {@code {{start.orderId}}} 模板
 */
public record ToolNodeConfig(
        @NotNull(message = "mcpToolId 不能为空")
        Long mcpToolId,

        Map<String, Object> args
) implements NodeConfig {

    public ToolNodeConfig {
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    @Override
    public NodeType type() {
        return NodeType.TOOL;
    }
}
