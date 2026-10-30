package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderType;

import java.util.Map;

/**
 * 更新提供商命令。
 */
public record ProviderUpdateCmd(
        ProviderType type,
        String name,
        String baseUrl,
        Map<String, Object> authConfig,
        Integer status
) {
}
