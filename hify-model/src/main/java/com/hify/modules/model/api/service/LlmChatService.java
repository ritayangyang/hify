package com.hify.modules.model.api.service;

import com.hify.modules.model.api.dto.LlmChatCmd;
import com.hify.modules.model.api.dto.LlmChatResult;

/**
 * LLM 非流式对话服务（其他模块只能依赖本接口）。
 * 按已保存的模型配置/提供商信息发起调用，失败统一抛 BizException。
 */
public interface LlmChatService {

    /**
     * 发起一次非流式对话。
     *
     * @throws com.hify.common.exception.BizException 模型/提供商不可用、上游 4xx/5xx、网络异常、响应非法
     */
    LlmChatResult chat(LlmChatCmd cmd);
}
