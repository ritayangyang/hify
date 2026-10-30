package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.StartNodeConfig;
import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.engine.NodeExecutor;
import com.hify.modules.workflow.domain.engine.NodeExecutorResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * START 节点：入参已在执行开始前写入变量池，这里只负责把入参记入节点执行记录。
 */
@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.START;
    }

    @Override
    public NodeExecutorResult execute(WorkflowNodeDto node, ExecutionContext context) {
        StartNodeConfig config = (StartNodeConfig) node.config();
        Map<String, Object> inputs = new LinkedHashMap<>(context.getStartInputs());
        // 声明的入参字段也记录一份，便于排查"缺参数"类问题
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("declaredInputs", config.inputs());
        return NodeExecutorResult.plain(outputs, inputs);
    }
}
