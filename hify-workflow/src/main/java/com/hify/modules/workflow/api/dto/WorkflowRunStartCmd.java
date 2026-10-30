package com.hify.modules.workflow.api.dto;

import java.util.Map;

/**
 * 触发工作流执行的命令。
 *
 * @param inputs 触发入参，键与 START 节点声明的 inputs 对应，如 {"userMessage":"我要退货"}；
 *               START 未声明入参时可为空
 */
public record WorkflowRunStartCmd(
        Map<String, Object> inputs
) {
}
