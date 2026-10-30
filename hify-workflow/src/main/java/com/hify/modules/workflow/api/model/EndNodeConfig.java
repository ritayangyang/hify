package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;
import jakarta.validation.constraints.NotBlank;

/**
 * 结束节点配置：组装工作流最终输出。
 *
 * @param replyTemplate 回复模板，支持 {@code {{nodeId.field}}} 引用上游结果
 */
public record EndNodeConfig(
        @NotBlank(message = "replyTemplate 不能为空")
        String replyTemplate
) implements NodeConfig {

    @Override
    public NodeType type() {
        return NodeType.END;
    }
}
