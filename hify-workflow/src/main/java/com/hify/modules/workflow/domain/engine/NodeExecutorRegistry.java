package com.hify.modules.workflow.domain.engine;

import com.hify.modules.workflow.api.enums.NodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行器注册表：Spring 自动收集所有 {@link NodeExecutor}，按节点类型分发。
 */
@Slf4j
@Component
public class NodeExecutorRegistry {

    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    public NodeExecutorRegistry(List<NodeExecutor> executorList) {
        for (NodeExecutor executor : executorList) {
            NodeExecutor previous = executors.put(executor.type(), executor);
            if (previous != null) {
                log.warn("节点类型 {} 存在多个执行器，{} 被覆盖为 {}",
                        executor.type(), previous.getClass().getSimpleName(),
                        executor.getClass().getSimpleName());
            }
        }
    }

    public Optional<NodeExecutor> find(NodeType type) {
        return Optional.ofNullable(executors.get(type));
    }
}
