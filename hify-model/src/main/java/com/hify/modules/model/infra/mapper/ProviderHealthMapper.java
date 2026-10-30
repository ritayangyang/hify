package com.hify.modules.model.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.model.infra.entity.ProviderHealth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供商健康状态 Mapper。
 */
@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealth> {
}
