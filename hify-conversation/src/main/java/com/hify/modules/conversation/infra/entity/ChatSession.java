package com.hify.modules.conversation.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话，对应表 t_chat_session。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_session")
public class ChatSession extends BaseEntity {

    /** 所属 Agent t_agent.id（不建物理外键） */
    private Long agentId;

    /** 会话标题，默认「新会话」，首条消息后取内容前 20 字 */
    private String title;

    /** 会话状态：ACTIVE */
    private String status;
}
