package com.hify.modules.workflow.infra.converter;

import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.infra.entity.Workflow;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Workflow PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WorkflowPoConverter {

    WorkflowDto toDto(Workflow entity);

    Workflow toEntity(WorkflowDto dto);
}
