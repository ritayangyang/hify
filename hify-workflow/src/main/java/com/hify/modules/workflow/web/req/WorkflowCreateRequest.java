package com.hify.modules.workflow.web.req;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.workflow.api.model.NodePosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建工作流请求：完整请求体（基本信息 + 完整节点和连线）。
 * 节点 config 先以 JSON 树接收，由 domain 层 NodeConfigParser 按 type 强类型解析。
 */
@Data
public class WorkflowCreateRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    /** 简介 */
    private String description;

    /** 状态：0 禁用，1 启用；为空默认启用 */
    private Integer status;

    @NotEmpty(message = "nodes 不能为空")
    @Valid
    private List<NodeRequest> nodes;

    @NotEmpty(message = "edges 不能为空")
    @Valid
    private List<EdgeRequest> edges;

    /**
     * 节点请求。
     */
    @Data
    public static class NodeRequest {

        /** 图内节点业务 id（start/n2…） */
        @NotBlank(message = "节点 id 不能为空")
        private String id;

        /** 节点类型：START/LLM/CONDITION/TOOL/KNOWLEDGE/END（字符串由后端枚举解析） */
        @NotBlank(message = "节点 type 不能为空")
        private String type;

        /** 按 type 多态的节点配置，结构由 NodeConfigParser 校验 */
        private JsonNode config;

        /** 画布坐标（前端使用） */
        private NodePosition position;
    }

    /**
     * 连线请求。
     */
    @Data
    public static class EdgeRequest {

        /** 图内连线业务 id（前端生成，可空） */
        private String id;

        @NotBlank(message = "连线 source 不能为空")
        private String source;

        /** 条件分支 id（b1/default）；普通节点唯一出线不传 */
        private String sourceHandle;

        @NotBlank(message = "连线 target 不能为空")
        private String target;
    }
}
