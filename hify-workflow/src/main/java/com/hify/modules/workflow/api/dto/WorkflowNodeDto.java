package com.hify.modules.workflow.api.dto;

import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.NodeConfig;
import com.hify.modules.workflow.api.model.NodePosition;

/**
 * 解析后的工作流节点。
 *
 * @param id       图内节点业务 id（start/n2…）
 * @param type     节点类型
 * @param config   按 type 多态解析后的强类型配置
 * @param position 画布坐标，可能为空
 */
public record WorkflowNodeDto(
        String id,
        NodeType type,
        NodeConfig config,
        NodePosition position
) {
}
