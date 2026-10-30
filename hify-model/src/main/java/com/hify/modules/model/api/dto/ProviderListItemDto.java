package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderHealthStatus;
import com.hify.modules.model.api.enums.ProviderType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 提供商列表行：基础信息 + 健康状态摘要 + 已启用模型数（分页列表批量装配，避免 N+1）。
 */
public record ProviderListItemDto(
        Long id,
        String code,
        ProviderType type,
        String name,
        String baseUrl,
        Map<String, Object> authConfig,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer failCount,
        Integer latencyMs,
        LocalDateTime lastSuccessAt,
        ProviderHealthStatus healthStatus,
        int enabledModelCount
) {
}
