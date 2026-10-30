package com.hify.modules.model.infra.converter;

import com.hify.modules.model.api.dto.ProviderHealthDto;
import com.hify.modules.model.infra.entity.ProviderHealth;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * ProviderHealth PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProviderHealthPoConverter {

    ProviderHealthDto toDto(ProviderHealth entity);

    ProviderHealth toEntity(ProviderHealthDto dto);
}
