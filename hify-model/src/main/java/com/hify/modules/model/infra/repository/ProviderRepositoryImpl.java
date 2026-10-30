package com.hify.modules.model.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.domain.repository.ProviderRepository;
import com.hify.modules.model.infra.converter.ProviderPoConverter;
import com.hify.modules.model.infra.entity.Provider;
import com.hify.modules.model.infra.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * 提供商仓储实现：PO ↔ DTO 转换收口在 infra 层。
 */
@Repository
@RequiredArgsConstructor
public class ProviderRepositoryImpl implements ProviderRepository {

    private final ProviderMapper providerMapper;
    private final ProviderPoConverter converter;

    @Override
    public ProviderDto insert(ProviderDto provider) {
        Provider entity = converter.toEntity(provider);
        providerMapper.insert(entity);
        return converter.toDto(entity);
    }

    @Override
    public boolean update(ProviderDto provider) {
        return providerMapper.updateById(converter.toEntity(provider)) > 0;
    }

    @Override
    public List<ProviderDto> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return providerMapper.selectBatchIds(ids).stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public ProviderDto findById(Long id) {
        Provider entity = providerMapper.selectById(id);
        return entity == null ? null : converter.toDto(entity);
    }

    @Override
    public boolean existsByCode(String code) {
        return providerMapper.selectCount(
                new LambdaQueryWrapper<Provider>().eq(Provider::getCode, code)) > 0;
    }

    @Override
    public PageResponse<ProviderDto> page(long page, long size, String name, ProviderType type, Integer status) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
                .like(StringUtils.hasText(name), Provider::getName, name)
                .eq(type != null, Provider::getType, type)
                .eq(status != null, Provider::getStatus, status)
                .orderByDesc(Provider::getId);

        Page<Provider> result = providerMapper.selectPage(new Page<>(page, size), wrapper);
        List<ProviderDto> records = result.getRecords().stream()
                .map(converter::toDto)
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public int deleteById(Long id) {
        return providerMapper.deleteById(id);
    }
}
