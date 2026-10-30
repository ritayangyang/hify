package com.hify.modules.conversation.web.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求（SSE 流式响应）。
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "content 不能为空")
    private String content;
}
