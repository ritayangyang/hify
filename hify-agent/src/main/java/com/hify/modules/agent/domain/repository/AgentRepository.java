package com.hify.modules.agent.domain.repository;

import com.hify.common.web.PageResponse;
import com.hify.modules.agent.api.dto.AgentDto;

/**
 * Agent 仓储端口（domain 定义，infra 实现），出入参只用 DTO。
 */
public interface AgentRepository {

    /** 插入并回填主键，返回带 id 的 DTO */
    AgentDto insert(AgentDto agent);

    /** 按主键整体更新 */
    boolean update(AgentDto agent);

    AgentDto findById(Long id);

    /**
     * 活跃记录中是否存在同名 Agent。
     *
     * @param excludeId 更新场景排除自身 id；创建场景传 null
     */
    boolean existsByName(String name, Long excludeId);

    PageResponse<AgentDto> page(long page, long size, String name, Integer status);

    /** 逻辑删除 */
    int deleteById(Long id);
}
