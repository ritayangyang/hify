package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 知识检索节点配置（knowledge 模块落地后生效，本期先存配置并做结构校验）。
 *
 * @param knowledgeIds 检索范围：知识库 id 列表
 * @param query        检索语句，支持模板引用，如 {@code {{start.userMessage}}}
 * @param topK         召回条数，为空用默认值
 */
public record KnowledgeNodeConfig(
        @NotEmpty(message = "knowledgeIds 至少选择一个知识库")
        List<Long> knowledgeIds,

        @NotBlank(message = "query 不能为空")
        String query,

        @Min(value = 1, message = "topK 最小为 1")
        @Max(value = 20, message = "topK 最大为 20")
        Integer topK
) implements NodeConfig {

    public KnowledgeNodeConfig {
        knowledgeIds = knowledgeIds == null ? List.of() : List.copyOf(knowledgeIds);
    }

    @Override
    public NodeType type() {
        return NodeType.KNOWLEDGE;
    }
}
