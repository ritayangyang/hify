package com.hify.modules.agent.api.dto;

/**
 * Agent 分页查询条件。
 */
public record AgentPageQuery(
        long page,
        long size,
        String name,
        Integer status
) {
}
