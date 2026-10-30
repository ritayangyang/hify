package com.hify.modules.agent.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.agent.domain.repository.AgentMcpToolRepository;
import com.hify.modules.agent.infra.entity.AgentMcpTool;
import com.hify.modules.agent.infra.mapper.AgentMcpToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent-MCP 工具 关联仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class AgentMcpToolRepositoryImpl implements AgentMcpToolRepository {

    private final AgentMcpToolMapper agentMcpToolMapper;

    @Override
    public List<Long> listIdsByAgentId(Long agentId) {
        return agentMcpToolMapper.selectList(
                        new LambdaQueryWrapper<AgentMcpTool>()
                                .eq(AgentMcpTool::getAgentId, agentId)
                                .orderByAsc(AgentMcpTool::getId))
                .stream()
                .map(AgentMcpTool::getMcpToolId)
                .toList();
    }

    @Override
    public Map<Long, Integer> countByAgentIds(Collection<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 单页最多 50 个 Agent，只取 agent_id 列在内存分组，免去自定义 XML
        return agentMcpToolMapper.selectList(
                        new LambdaQueryWrapper<AgentMcpTool>()
                                .select(AgentMcpTool::getAgentId)
                                .in(AgentMcpTool::getAgentId, agentIds))
                .stream()
                .collect(Collectors.groupingBy(AgentMcpTool::getAgentId,
                        Collectors.summingInt(relation -> 1)));
    }

    @Override
    public int deleteByAgentId(Long agentId) {
        return agentMcpToolMapper.delete(
                new LambdaQueryWrapper<AgentMcpTool>().eq(AgentMcpTool::getAgentId, agentId));
    }

    @Override
    public void insertBatch(Long agentId, Collection<Long> mcpToolIds) {
        if (mcpToolIds == null || mcpToolIds.isEmpty()) {
            return;
        }
        for (Long mcpToolId : mcpToolIds) {
            AgentMcpTool relation = new AgentMcpTool();
            relation.setAgentId(agentId);
            relation.setMcpToolId(mcpToolId);
            agentMcpToolMapper.insert(relation);
        }
    }
}
