package com.hify.modules.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent-知识库 关联，对应表 t_agent_knowledge。
 * 资源表由 knowledge 模块后续创建，本期只存资源 id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_knowledge")
public class AgentKnowledge extends BaseEntity {

    /** Agent t_agent.id */
    private Long agentId;

    /** 知识库 id（knowledge 模块落地后生效） */
    private Long knowledgeId;
}
