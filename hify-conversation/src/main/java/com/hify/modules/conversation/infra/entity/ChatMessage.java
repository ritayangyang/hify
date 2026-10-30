package com.hify.modules.conversation.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息，对应表 t_chat_message。system 消息只注入上下文，不持久化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_message")
public class ChatMessage extends BaseEntity {

    /** 所属会话 t_chat_session.id（不建物理外键） */
    private Long sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 消息文本 */
    private String content;

    /** token 总数（prompt+completion），未知为 null */
    private Integer tokens;

    /** 结束原因：stop / length */
    private String finishReason;

    /** 耗时毫秒 */
    private Integer latencyMs;
}
