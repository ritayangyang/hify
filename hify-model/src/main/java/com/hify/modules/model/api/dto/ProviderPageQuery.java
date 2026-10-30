package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderType;

/**
 * 提供商分页查询条件。
 */
public record ProviderPageQuery(
        long page,
        long size,
        String name,
        ProviderType type,
        Integer status
) {
}
