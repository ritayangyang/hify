package com.hify.modules.model.api.dto;

import com.hify.modules.model.api.enums.ProviderType;

/**
 * 连通性测试入参。可来自未保存的表单，故直接传连接信息而非 providerId。
 *
 * @param type    提供商类型（决定协议分发）
 * @param baseUrl API 基础地址
 * @param apiKey  认证密钥；OLLAMA 可为空
 */
public record ConnectionTestCmd(
        ProviderType type,
        String baseUrl,
        String apiKey
) {
}
