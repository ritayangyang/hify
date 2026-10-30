package com.hify.modules.conversation.domain;

/**
 * SSE 客户端断开或 emitter 已终态导致对话中止。
 * 非业务故障：onDelta 回调中抛出后会让模型层取消上游 HTTP 调用，
 * 上层静默收尾，不推送 error 事件、不打 ERROR 日志。
 */
public class ChatClientAbortedException extends RuntimeException {

    public ChatClientAbortedException(String message) {
        super(message);
    }
}
