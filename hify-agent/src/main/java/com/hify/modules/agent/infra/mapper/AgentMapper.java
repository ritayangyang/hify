package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper。
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
