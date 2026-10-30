package com.hify.modules.model.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.infra.converter.ModelConfigPoConverter;
import com.hify.modules.model.infra.entity.ModelConfig;
import com.hify.modules.model.infra.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型配置仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class ModelConfigRepositoryImpl implements ModelConfigRepository {

    private final ModelConfigMapper modelConfigMapper;
    private final ModelConfigPoConverter converter;

    @Override
    public List<ModelConfigDto> listByProviderId(Long providerId) {
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfig>()
                                .eq(ModelConfig::getProviderId, providerId)
                                .orderByAsc(ModelConfig::getModelType)
                                .orderByDesc(ModelConfig::getId))
                .stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public List<ModelConfigDto> listEnabledChat() {
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfig>()
                                .eq(ModelConfig::getStatus, 1)
                                .eq(ModelConfig::getModelType, 1)
                                .orderByAsc(ModelConfig::getId))
                .stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public List<ModelConfigDto> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfig>()
                                .in(ModelConfig::getId, ids))
                .stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public Map<Long, Integer> countEnabledByProviderIds(Collection<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 只取 provider_id 一列在内存分组：单页最多 50 个提供商，数据量可控，免去自定义 XML
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfig>()
                                .select(ModelConfig::getProviderId)
                                .in(ModelConfig::getProviderId, providerIds)
                                .eq(ModelConfig::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(ModelConfig::getProviderId,
                        Collectors.summingInt(m -> 1)));
    }

    @Override
    public int deleteByProviderId(Long providerId) {
        return modelConfigMapper.delete(
                new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getProviderId, providerId));
    }
}
