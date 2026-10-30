package com.hify.modules.model.domain.repository;

import com.hify.modules.model.api.dto.ProviderHealthDto;

import java.util.Collection;
import java.util.List;

/**
 * 提供商健康状态仓储端口。
 */
public interface ProviderHealthRepository {

    ProviderHealthDto findByProviderId(Long providerId);

    /** 按一批提供商批量取健康记录（列表页装配，避免 N+1） */
    List<ProviderHealthDto> listByProviderIds(Collection<Long> providerIds);

    /** 不存在则插入，存在则按 providerId 更新 */
    ProviderHealthDto saveOrUpdate(ProviderHealthDto health);

    /** 提供商删除时一并清理（物理删除） */
    int deleteByProviderId(Long providerId);
}
