package com.hify.modules.agent.infra.converter;

import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.infra.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Agent PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AgentPoConverter {

    AgentDto toDto(Agent entity);

    Agent toEntity(AgentDto dto);
}
