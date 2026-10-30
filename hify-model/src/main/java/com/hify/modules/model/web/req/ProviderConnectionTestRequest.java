package com.hify.modules.model.web.req;

import com.hify.modules.model.api.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 连通性测试请求。
 */
@Data
public class ProviderConnectionTestRequest {

    @NotNull(message = "提供商类型不能为空")
    private ProviderType type;

    @NotBlank(message = "baseUrl 不能为空")
    private String baseUrl;

    /** 认证密钥；OLLAMA 无认证，可不传 */
    private String apiKey;
}
