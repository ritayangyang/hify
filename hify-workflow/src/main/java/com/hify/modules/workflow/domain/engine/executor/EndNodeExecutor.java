package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.EndNodeConfig;
import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.engine.NodeExecutor;
import com.hify.modules.workflow.domain.engine.NodeExecutorResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * END 节点：渲染回复模板得到工作流最终输出（reply）。
 */
@Component
public class EndNodeExecutor implements NodeExecutor {

    public static final String REPLY_KEY = "reply";

    @Override
    public NodeType type() {
        return NodeType.END;
    }

    @Override
    public NodeExecutorResult execute(WorkflowNodeDto node, ExecutionContext context) {
        EndNodeConfig config = (EndNodeConfig) node.config();
        String reply = context.resolve(node.id(), config.replyTemplate());
        return NodeExecutorResult.plain(Map.of(REPLY_KEY, reply), Map.of());
    }
}
