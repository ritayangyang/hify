package com.hify.modules.model.api.service;

import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ConnectionTestCmd;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.dto.ProviderCreateCmd;
import com.hify.modules.model.api.dto.ProviderDetailDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.dto.ProviderListItemDto;
import com.hify.modules.model.api.dto.ProviderPageQuery;
import com.hify.modules.model.api.dto.ProviderUpdateCmd;

/**
 * 模型提供商对外服务接口（其他模块只能依赖本接口）。
 */
public interface ProviderService {

    /**
     * 创建提供商，并初始化健康状态记录。
     */
    ProviderDto create(ProviderCreateCmd cmd);

    /**
     * 分页查询提供商，每行带健康状态摘要与已启用模型数。
     */
    PageResponse<ProviderListItemDto> page(ProviderPageQuery query);

    /**
     * 提供商详情：含模型配置列表与健康状态。
     */
    ProviderDetailDto getDetail(Long id);

    /**
     * 更新提供商。
     */
    ProviderDto update(Long id, ProviderUpdateCmd cmd);

    /**
     * 删除提供商，同时逻辑删除其模型配置与健康状态。
     */
    void delete(Long id);

    /**
     * 对已保存的提供商做连通性测试，并回写健康状态。
     */
    ConnectionTestResult testConnection(Long id);

    /**
     * 连通性测试（表单未保存时使用）：根据 {@link ConnectionTestCmd#type()} 分发，
     * 超时 10 秒，任何失败都转换为 ConnectionTestResult，不抛异常。
     */
    ConnectionTestResult testConnection(ConnectionTestCmd cmd);
}
