package com.hify.modules.agent.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResponse;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.domain.repository.AgentRepository;
import com.hify.modules.agent.infra.converter.AgentPoConverter;
import com.hify.modules.agent.infra.entity.Agent;
import com.hify.modules.agent.infra.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 仓储实现：PO ↔ DTO 转换收口在 infra 层。
 */
@Repository
@RequiredArgsConstructor
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;
    private final AgentPoConverter converter;

    @Override
    public AgentDto insert(AgentDto agent) {
        Agent entity = converter.toEntity(agent);
        agentMapper.insert(entity);
        return converter.toDto(entity);
    }

    @Override
    public boolean update(AgentDto agent) {
        return agentMapper.updateById(converter.toEntity(agent)) > 0;
    }

    @Override
    public AgentDto findById(Long id) {
        Agent entity = agentMapper.selectById(id);
        return entity == null ? null : converter.toDto(entity);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return agentMapper.selectCount(
                        new LambdaQueryWrapper<Agent>()
                                .eq(Agent::getName, name)
                                .ne(excludeId != null, Agent::getId, excludeId))
                > 0;
    }

    @Override
    public PageResponse<AgentDto> page(long page, long size, String name, Integer status) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .like(StringUtils.hasText(name), Agent::getName, name)
                .eq(status != null, Agent::getStatus, status)
                .orderByDesc(Agent::getId);

        Page<Agent> result = agentMapper.selectPage(new Page<>(page, size), wrapper);
        List<AgentDto> records = result.getRecords().stream()
                .map(converter::toDto)
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public int deleteById(Long id) {
        return agentMapper.deleteById(id);
    }
}
