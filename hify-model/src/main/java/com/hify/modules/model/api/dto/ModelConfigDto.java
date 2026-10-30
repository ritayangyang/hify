package com.hify.modules.model.api.dto;

import java.util.Map;

/**
 * 模型配置出参。展示名 name 与真实模型标识 modelId 分开。
 */
public record ModelConfigDto(
        Long id,
        Long providerId,
        String name,
        String modelId,
        Integer modelType,
        Integer status,
        Map<String, Object> extraParams
) {
}
