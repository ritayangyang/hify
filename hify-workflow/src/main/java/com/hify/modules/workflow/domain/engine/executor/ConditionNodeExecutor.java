package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.ConditionNodeConfig;
import com.hify.modules.workflow.domain.engine.ConditionEvaluator;
import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.engine.NodeExecutor;
import com.hify.modules.workflow.domain.engine.NodeExecutorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CONDITION 节点：按配置顺序求各分支表达式，第一个为真的分支 id 作为 selectedHandle；
 * 全部未命中时 selectedHandle=null，由核心循环尝试 sourceHandle=default 的出线。
 * 命中即短路：后续分支不再渲染求值（严格模板语义下，后续分支引用的变量可能
 * 不在本次执行路径上，继续解析会把本应成功的路由误判为失败）。
 */
@Component
@RequiredArgsConstructor
public class ConditionNodeExecutor implements NodeExecutor {

    private final ConditionEvaluator conditionEvaluator;

    @Override
    public NodeType type() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeExecutorResult execute(WorkflowNodeDto node, ExecutionContext context) {
        ConditionNodeConfig config = (ConditionNodeConfig) node.config();

        String matchedBranch = null;
        Map<String, Object> evaluatedExpressions = new LinkedHashMap<>();
        for (ConditionNodeConfig.Branch branch : config.branches()) {
            // 先渲染变量，再求值
            String resolved = context.resolve(node.id(), branch.expression());
            boolean matched = conditionEvaluator.evaluate(node.id(), branch.id(), resolved);
            evaluatedExpressions.put(branch.id(), resolved + " => " + matched);
            if (matched) {
                matchedBranch = branch.id();
                break;
            }
        }

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("matchedBranch", matchedBranch);
        return NodeExecutorResult.condition(matchedBranch, outputs, evaluatedExpressions);
    }
}
