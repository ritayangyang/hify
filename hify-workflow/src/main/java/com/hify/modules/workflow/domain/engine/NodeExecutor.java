package com.hify.modules.workflow.domain.engine;

import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;

/**
 * 节点执行器统一接口：一种节点类型一个实现，无状态单例。
 * 新增节点类型只需新增一个实现并注册为 Spring Bean，核心循环无需改动。
 */
public interface NodeExecutor {

    /** 支持的节点类型 */
    NodeType type();

    /**
     * 执行节点；失败直接抛 {@link com.hify.common.exception.BizException}，
     * 由核心循环统一落 FAILED 记录。
     */
    NodeExecutorResult execute(WorkflowNodeDto node, ExecutionContext context);
}
