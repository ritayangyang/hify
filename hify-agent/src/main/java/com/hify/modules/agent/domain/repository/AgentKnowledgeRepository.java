package com.hify.modules.agent.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Agent-知识库 关联仓储端口。
 */
public interface AgentKnowledgeRepository {

    /** 某 Agent 关联的全部知识库 id，按关联 id 升序 */
    List<Long> listIdsByAgentId(Long agentId);

    /**
     * 批量统计各 Agent 的关联数。
     *
     * @return agentId → 数量；无关联的 Agent 不出现在 Map 中
     */
    Map<Long, Integer> countByAgentIds(Collection<Long> agentIds);

    /** 逻辑删除某 Agent 的全部关联 */
    int deleteByAgentId(Long agentId);

    /** 批量插入关联（调用方负责先清旧关联并保证 id 去重） */
    void insertBatch(Long agentId, Collection<Long> knowledgeIds);
}
