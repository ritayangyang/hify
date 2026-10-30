package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent-MCP 工具 关联，对应表 t_agent_mcp_tool。
 * 资源表由 mcp 模块后续创建，本期只存资源 id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_mcp_tool")
public class AgentMcpTool extends BaseEntity {

    /** Agent t_agent.id */
    private Long agentId;

    /** MCP 工具 id（mcp 模块落地后生效） */
    private Long mcpToolId;
}
