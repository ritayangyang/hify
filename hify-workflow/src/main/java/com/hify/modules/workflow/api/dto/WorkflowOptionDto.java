package com.hify.modules.workflow.api.dto;

/**
 * 工作流下拉选项（只取 id + 名称，供 Agent 绑定等跨模块场景使用）。
 *
 * @param id   工作流 id
 * @param name 工作流名称
 */
public record WorkflowOptionDto(
        Long id,
        String name
) {
}
