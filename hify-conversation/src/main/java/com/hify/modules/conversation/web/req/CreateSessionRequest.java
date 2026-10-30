package com.hify.modules.conversation.web.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class CreateSessionRequest {

    @NotNull(message = "agentId 不能为空")
    private Long agentId;
}
