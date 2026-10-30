package com.hify.modules.model.api.service;

import com.hify.common.exception.BizException;
import com.hify.modules.model.api.dto.LlmStreamCmd;
import com.hify.modules.model.api.dto.LlmStreamResult;

import java.util.function.Consumer;

/**
 * 流式对话服务（其他模块通过本接口调用，禁止直接依赖 model 的 domain/infra）。
 */
public interface LlmStreamService {

    /**
     * 发起流式对话，每收到一段增量文本回调一次 onDelta，结束后返回汇总结果。
     *
     * @param onDelta 增量文本回调；回调内抛异常会取消上游 HTTP 调用（用于客户端断开即停）
     * @throws BizException 建流前的参数/鉴权错误、上游 4xx/5xx、网络异常、空回复
     */
    LlmStreamResult streamChat(LlmStreamCmd cmd, Consumer<String> onDelta);
}
