package com.hify.modules.model.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.model.infra.entity.Provider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型提供商 Mapper。
 */
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {
}
