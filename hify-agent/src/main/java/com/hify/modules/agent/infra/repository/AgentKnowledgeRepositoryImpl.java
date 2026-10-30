package com.hify.modules.agent.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.agent.domain.repository.AgentKnowledgeRepository;
import com.hify.modules.agent.infra.entity.AgentKnowledge;
import com.hify.modules.agent.infra.mapper.AgentKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent-知识库 关联仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class AgentKnowledgeRepositoryImpl implements AgentKnowledgeRepository {

    private final AgentKnowledgeMapper agentKnowledgeMapper;

    @Override
    public List<Long> listIdsByAgentId(Long agentId) {
        return agentKnowledgeMapper.selectList(
                        new LambdaQueryWrapper<AgentKnowledge>()
                                .eq(AgentKnowledge::getAgentId, agentId)
                                .orderByAsc(AgentKnowledge::getId))
                .stream()
                .map(AgentKnowledge::getKnowledgeId)
                .toList();
    }

    @Override
    public Map<Long, Integer> countByAgentIds(Collection<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 单页最多 50 个 Agent，只取 agent_id 列在内存分组，免去自定义 XML
        return agentKnowledgeMapper.selectList(
                        new LambdaQueryWrapper<AgentKnowledge>()
                                .select(AgentKnowledge::getAgentId)
                                .in(AgentKnowledge::getAgentId, agentIds))
                .stream()
                .collect(Collectors.groupingBy(AgentKnowledge::getAgentId,
                        Collectors.summingInt(relation -> 1)));
    }

    @Override
    public int deleteByAgentId(Long agentId) {
        return agentKnowledgeMapper.delete(
                new LambdaQueryWrapper<AgentKnowledge>().eq(AgentKnowledge::getAgentId, agentId));
    }

    @Override
    public void insertBatch(Long agentId, Collection<Long> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return;
        }
        for (Long knowledgeId : knowledgeIds) {
            AgentKnowledge relation = new AgentKnowledge();
            relation.setAgentId(agentId);
            relation.setKnowledgeId(knowledgeId);
            agentKnowledgeMapper.insert(relation);
        }
    }
}
