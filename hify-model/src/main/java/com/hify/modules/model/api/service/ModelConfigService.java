package com.hify.modules.model.api.service;

import com.hify.modules.model.api.dto.ModelOptionDto;

import java.util.Collection;
import java.util.List;

/**
 * 模型配置只读服务（其他模块只能依赖本接口）。
 * 模型配置的写入接口尚未实现；本服务用于 Agent 等模块的模型下拉与绑定信息装配。
 */
public interface ModelConfigService {

    /**
     * 所有「已启用的对话模型」选项（status=1、model_type=1、未删除），按 id 升序。
     * 用于 Agent 表单的模型下拉。
     */
    List<ModelOptionDto> listEnabledChatOptions();

    /**
     * 按 id 批量查询选项（不过滤状态/类型），用于已存在绑定的信息装配——
     * 模型被停用后 Agent 详情/列表仍应展示其名称。
     *
     * @param ids 模型配置 id 集合
     * @return 命中的选项；空集合直接返回空列表
     */
    List<ModelOptionDto> listOptionsByIds(Collection<Long> ids);
}
