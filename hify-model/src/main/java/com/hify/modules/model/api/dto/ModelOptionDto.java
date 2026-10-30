package com.hify.modules.model.api.dto;

/**
 * 模型下拉/装配选项。跨模块只读视图，附带提供商名称便于直接展示。
 *
 * @param id           模型配置 id
 * @param name         模型展示名
 * @param modelId      真实模型标识
 * @param modelType    模型类型：1 chat 对话，2 embedding
 * @param status       状态：0 禁用，1 启用
 * @param providerId   所属提供商 id
 * @param providerName 所属提供商名称
 */
public record ModelOptionDto(
        Long id,
        String name,
        String modelId,
        Integer modelType,
        Integer status,
        Long providerId,
        String providerName
) {
}
