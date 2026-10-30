package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentMcpTool;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent-MCP 工具 关联 Mapper。
 */
@Mapper
public interface AgentMcpToolMapper extends BaseMapper<AgentMcpTool> {
}
