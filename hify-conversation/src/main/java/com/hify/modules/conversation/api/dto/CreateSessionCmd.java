package com.hify.modules.conversation.api.dto;

/**
 * 创建对话会话命令。
 *
 * @param agentId 会话绑定的 Agent id
 */
public record CreateSessionCmd(
        Long agentId
) {
}
