package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent-知识库 关联 Mapper。
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledge> {
}
