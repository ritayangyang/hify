package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderType;

import java.util.Map;

/**
 * 创建提供商命令。
 */
public record ProviderCreateCmd(
        String code,
        ProviderType type,
        String name,
        String baseUrl,
        Map<String, Object> authConfig,
        Integer status
) {
}
