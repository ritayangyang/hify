package com.hify.modules.workflow.api.model;

import com.hify.modules.workflow.api.enums.NodeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * LLM 节点配置。
 *
 * @param modelConfigId  绑定模型 t_model_config.id（必须为已启用的对话模型）
 * @param promptTemplate 提示词模板，支持 {@code {{nodeId.field}}} 引用
 * @param temperature    采样温度 0-2，为空用模型默认值
 * @param maxTokens      最大生成 token 数，为空用模型默认值
 * @param outputKey      本节点结果写入小黑板的键名，供后续节点引用
 */
public record LlmNodeConfig(
        @NotNull(message = "modelConfigId 不能为空")
        Long modelConfigId,

        @NotBlank(message = "promptTemplate 不能为空")
        String promptTemplate,

        @DecimalMin(value = "0", message = "temperature 最小为 0")
        @DecimalMax(value = "2", message = "temperature 最大为 2")
        BigDecimal temperature,

        @Min(value = 1, message = "maxTokens 最小为 1")
        Integer maxTokens,

        @NotBlank(message = "outputKey 不能为空")
        String outputKey
) implements NodeConfig {

    @Override
    public NodeType type() {
        return NodeType.LLM;
    }
}
