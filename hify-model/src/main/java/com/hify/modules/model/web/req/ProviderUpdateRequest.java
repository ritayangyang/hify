package com.hify.modules.model.web.req;

import com.hify.modules.model.api.enums.ProviderType;
import lombok.Data;

import java.util.Map;

/**
 * 更新提供商请求（code 不可修改）。
 */
@Data
public class ProviderUpdateRequest {

    private ProviderType type;

    private String name;

    private String baseUrl;

    /** 认证配置；传 null 表示不更新 */
    private Map<String, Object> authConfig;

    private Integer status;
}
