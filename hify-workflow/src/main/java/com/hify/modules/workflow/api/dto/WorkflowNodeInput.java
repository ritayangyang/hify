package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.workflow.api.model.NodePosition;

/**
 * 入参中的原始节点：config 保持 JSON 树，由 domain 层 NodeConfigParser
 * 按 type 解析成强类型 {@link com.hify.modules.workflow.api.model.NodeConfig}。
 *
 * @param id       图内节点业务 id
 * @param type     节点类型名（START/LLM/…）
 * @param config   节点配置原始 JSON，可能为空
 * @param position 画布坐标
 */
public record WorkflowNodeInput(
        String id,
        String type,
        JsonNode config,
        NodePosition position
) {
}
