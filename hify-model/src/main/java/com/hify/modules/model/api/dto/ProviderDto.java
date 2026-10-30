package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 提供商出参。authConfig 对前端返回时必须脱敏（见 ProviderServiceImpl）。
 */
public record ProviderDto(
        Long id,
        String code,
        ProviderType type,
        String name,
        String baseUrl,
        Map<String, Object> authConfig,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
