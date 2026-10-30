package com.hify.modules.workflow.api.enums;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

/**
 * 工作流节点类型（MVP 范围）。
 * 存库为 {@code t_workflow_node.node_type} 字符串，跨模块传递用枚举名。
 */
public enum NodeType {

    /** 开始节点：声明工作流入参，全局有且仅有一个 */
    START,

    /** LLM 调用节点：绑定模型配置，渲染提示词模板后调用大模型 */
    LLM,

    /** 条件分支节点：按表达式命中分支决定下一跳，出口用 sourceHandle 区分 */
    CONDITION,

    /** 工具调用节点：对接 MCP 工具（mcp 模块落地后生效） */
    TOOL,

    /** 知识检索节点：向量召回知识库片段（knowledge 模块落地后生效） */
    KNOWLEDGE,

    /** 结束节点：组装最终回复，全局有且仅有一个 */
    END;

    /**
     * 宽松解析前端传入的类型名（忽略大小写与首尾空白）；
     * 未知类型是客户端参数问题，抛 PARAM_ERROR 而非让 Jackson 归到 500。
     */
    public static NodeType fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点 type 不能为空");
        }
        try {
            return NodeType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未知节点类型: " + name);
        }
    }
}
