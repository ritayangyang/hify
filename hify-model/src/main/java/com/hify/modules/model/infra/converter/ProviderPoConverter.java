package com.hify.modules.model.infra.converter;

import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.infra.entity.Provider;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Provider PO（实体） ↔ DTO 转换。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProviderPoConverter {

    ProviderDto toDto(Provider entity);

    Provider toEntity(ProviderDto dto);
}
