package com.hify.modules.model.infra.converter;

import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.infra.entity.ModelConfig;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * ModelConfig PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ModelConfigPoConverter {

    ModelConfigDto toDto(ModelConfig entity);

    ModelConfig toEntity(ModelConfigDto dto);
}
