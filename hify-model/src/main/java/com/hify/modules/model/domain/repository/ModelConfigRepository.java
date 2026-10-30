package com.hify.modules.model.domain.repository;

import com.hify.modules.model.api.dto.ModelConfigDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 模型配置仓储端口。
 */
public interface ModelConfigRepository {

    List<ModelConfigDto> listByProviderId(Long providerId);

    /** 全部「已启用的对话模型」（status=1、model_type=1），按 id 升序 */
    List<ModelConfigDto> listEnabledChat();

    /** 按 id 批量查询（状态/类型不过滤）；空集合返回空列表 */
    List<ModelConfigDto> listByIds(Collection<Long> ids);

    /**
     * 批量统计各提供商下「已启用」（status=1、未删除）的模型数。
     *
     * @return providerId → 模型数；没有启用模型的提供商不出现在 Map 中
     */
    Map<Long, Integer> countEnabledByProviderIds(Collection<Long> providerIds);

    /** 按提供商逻辑删除全部模型配置 */
    int deleteByProviderId(Long providerId);
}
