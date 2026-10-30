package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 条件分支节点配置。
 * 分支表达式只负责"判断"，命中分支通向哪个节点由 edge 的 sourceHandle=branchId 表达，
 * 保证连接关系只有一处事实来源。
 *
 * @param branches 有序分支列表，按顺序取第一个命中的；未命中任何分支走 sourceHandle=default 的边
 */
public record ConditionNodeConfig(
        @NotEmpty(message = "branches 至少配置一个分支")
        @Valid
        List<Branch> branches
) implements NodeConfig {

    public ConditionNodeConfig {
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    @Override
    public NodeType type() {
        return NodeType.CONDITION;
    }

    /**
     * @param id         分支 id，图内节点内唯一，对应 edge.sourceHandle
     * @param expression 条件表达式，如 {@code {{n2.intent}} == '退货'}
     */
    public record Branch(
            @NotBlank(message = "分支 id 不能为空")
            String id,

            @NotBlank(message = "分支 expression 不能为空")
            String expression
    ) {
    }
}
