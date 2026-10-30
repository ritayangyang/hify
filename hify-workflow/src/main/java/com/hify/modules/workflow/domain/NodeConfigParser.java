package com.hify.modules.workflow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.NodeConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 节点配置解析器：节点配置在传输/存储中都是 JSON，只有这里负责它与
 * {@link NodeConfig} sealed 强类型体系之间的双向转换。
 *
 * <p>多态标记 type 在节点外壳上，config 对象本身不带 type；
 * 解析前把 type 并入 config JSON，交给 Jackson 按 {@code @JsonSubTypes} 选择子类 record。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeConfigParser {

    /** 多态标记字段名，与 NodeConfig 上的 @JsonTypeInfo property 一致 */
    private static final String TYPE_FIELD = "type";

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 解析请求中的节点配置：未知类型/结构非法/字段约束不通过一律归 400。
     */
    public NodeConfig parseInput(String nodeId, NodeType type, JsonNode rawConfig) {
        NodeConfig config = convert(nodeId, type, rawConfig, ErrorCode.PARAM_ERROR);
        validate(nodeId, config);
        return config;
    }

    /**
     * 回读库中节点配置。数据写库时已经过校验，此处再失败说明数据损坏，归 500。
     */
    public NodeConfig fromStorage(String nodeId, NodeType type, JsonNode storedConfig) {
        return convert(nodeId, type, storedConfig, ErrorCode.INTERNAL_ERROR);
    }

    /**
     * 强类型配置序列化为存储树：剥离多态标记 type（节点类型独立成 node_type 列，不重复存储）。
     */
    public JsonNode toTree(NodeConfig config) {
        JsonNode tree = objectMapper.valueToTree(config);
        if (!tree.isObject()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "节点配置序列化结果不是 JSON 对象: " + config.getClass().getSimpleName());
        }
        ((ObjectNode) tree).remove(TYPE_FIELD);
        return tree;
    }

    private NodeConfig convert(String nodeId, NodeType type, JsonNode rawConfig, ErrorCode errorCode) {
        ObjectNode merged;
        if (rawConfig == null || rawConfig.isNull()) {
            merged = objectMapper.createObjectNode();
        } else if (rawConfig.isObject()) {
            merged = rawConfig.deepCopy();
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点 " + nodeId + " 的 config 必须是 JSON 对象");
        }
        merged.put(TYPE_FIELD, type.name());
        try {
            return objectMapper.treeToValue(merged, NodeConfig.class);
        } catch (JsonProcessingException e) {
            log.warn("节点配置解析失败: nodeId={}, type={}, reason={}", nodeId, type, e.getOriginalMessage());
            throw new BizException(errorCode,
                    "节点 " + nodeId + " 配置解析失败: " + e.getOriginalMessage(), e);
        }
    }

    private void validate(String nodeId, NodeConfig config) {
        Set<ConstraintViolation<NodeConfig>> violations = validator.validate(config);
        if (violations.isEmpty()) {
            return;
        }
        String detail = violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        throw new BizException(ErrorCode.PARAM_ERROR, "节点 " + nodeId + " 配置校验失败: " + detail);
    }
}
