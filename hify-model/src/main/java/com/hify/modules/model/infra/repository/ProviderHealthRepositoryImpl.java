package com.hify.modules.model.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.model.api.dto.ProviderHealthDto;
import com.hify.modules.model.domain.repository.ProviderHealthRepository;
import com.hify.modules.model.infra.converter.ProviderHealthPoConverter;
import com.hify.modules.model.infra.entity.ProviderHealth;
import com.hify.modules.model.infra.mapper.ProviderHealthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 提供商健康状态仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class ProviderHealthRepositoryImpl implements ProviderHealthRepository {

    private final ProviderHealthMapper healthMapper;
    private final ProviderHealthPoConverter converter;

    @Override
    public ProviderHealthDto findByProviderId(Long providerId) {
        ProviderHealth entity = healthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>()
                        .eq(ProviderHealth::getProviderId, providerId));
        return entity == null ? null : converter.toDto(entity);
    }

    @Override
    public List<ProviderHealthDto> listByProviderIds(Collection<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Collections.emptyList();
        }
        return healthMapper.selectList(
                        new LambdaQueryWrapper<ProviderHealth>()
                                .in(ProviderHealth::getProviderId, providerIds))
                .stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public ProviderHealthDto saveOrUpdate(ProviderHealthDto health) {
        ProviderHealth existing = healthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>()
                        .eq(ProviderHealth::getProviderId, health.providerId()));
        if (existing == null) {
            ProviderHealth entity = converter.toEntity(health);
            healthMapper.insert(entity);
            return converter.toDto(entity);
        }
        existing.setFailCount(health.failCount());
        existing.setLatencyMs(health.latencyMs());
        existing.setLastSuccessAt(health.lastSuccessAt());
        healthMapper.updateById(existing);
        return converter.toDto(existing);
    }

    @Override
    public int deleteByProviderId(Long providerId) {
        return healthMapper.delete(
                new LambdaQueryWrapper<ProviderHealth>().eq(ProviderHealth::getProviderId, providerId));
    }
}
