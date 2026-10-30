package com.hify.modules.model.api.dto;

import java.util.List;

/**
 * 提供商详情：基础信息 + 模型配置列表 + 健康状态。
 */
public record ProviderDetailDto(
        ProviderDto provider,
        List<ModelConfigDto> modelConfigs,
        ProviderHealthDto health
) {
}
