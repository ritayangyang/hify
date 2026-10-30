package com.hify.modules.model.api.dto;

import java.time.LocalDateTime;

/**
 * 提供商健康状态出参。
 */
public record ProviderHealthDto(
        Long providerId,
        Integer failCount,
        Integer latencyMs,
        LocalDateTime lastSuccessAt
) {
}
