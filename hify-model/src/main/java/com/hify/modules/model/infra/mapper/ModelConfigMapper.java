package com.hify.modules.model.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.model.infra.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper。
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {
}
