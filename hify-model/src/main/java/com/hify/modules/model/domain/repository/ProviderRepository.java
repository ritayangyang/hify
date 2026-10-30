package com.hify.modules.model.domain.repository;

import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.enums.ProviderType;

import java.util.Collection;
import java.util.List;

/**
 * 提供商仓储端口（domain 定义，infra 实现），出入参只用 DTO。
 */
public interface ProviderRepository {

    /** 插入并回填主键，返回带 id 的 DTO */
    ProviderDto insert(ProviderDto provider);

    /** 按 id 批量查询；空集合返回空列表 */
    List<ProviderDto> listByIds(Collection<Long> ids);

    /** 按主键更新非空字段 */
    boolean update(ProviderDto provider);

    ProviderDto findById(Long id);

    boolean existsByCode(String code);

    PageResponse<ProviderDto> page(long page, long size, String name, ProviderType type, Integer status);

    /** 逻辑删除 */
    int deleteById(Long id);
}
