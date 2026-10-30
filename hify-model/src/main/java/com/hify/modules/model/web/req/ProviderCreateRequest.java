package com.hify.modules.model.web.req;

import com.hify.modules.model.api.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.Map;

/**
 * 创建提供商请求。
 */
@Data
public class ProviderCreateRequest {

    /** 提供商编码，留空时按 type 自动生成（如 OPENAI -> openai），用于 fallback 路由 */
    private String code;

    @NotNull(message = "type 不能为空")
    private ProviderType type;

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "baseUrl 不能为空")
    private String baseUrl;

    /** 认证配置，如 {"apiKey":"sk-xxx"} */
    private Map<String, Object> authConfig;

    /** 状态：0 禁用，1 启用；为空默认启用 */
    private Integer status;
}
