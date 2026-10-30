package com.hify.modules.workflow.web.req;

import lombok.Data;

import java.util.Map;

/**
 * 触发工作流执行请求。
 */
@Data
public class WorkflowRunStartRequest {

    /** 触发入参，键与 START 节点声明的 inputs 对应；START 未声明时可省略 */
    private Map<String, Object> inputs;
}
