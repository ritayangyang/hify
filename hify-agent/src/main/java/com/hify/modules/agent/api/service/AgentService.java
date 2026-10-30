package com.hify.modules.agent.api.service;

import com.hify.common.web.PageResponse;
import com.hify.modules.agent.api.dto.AgentCreateCmd;
import com.hify.modules.agent.api.dto.AgentDetailDto;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.api.dto.AgentListItemDto;
import com.hify.modules.agent.api.dto.AgentPageQuery;
import com.hify.modules.agent.api.dto.AgentUpdateCmd;

/**
 * Agent 管理对外服务接口。
 */
public interface AgentService {

    /** 创建 Agent，同时整体写入知识库/MCP 关联 */
    AgentDto create(AgentCreateCmd cmd);

    /** 分页查询 Agent，每行带绑定模型摘要与关联资源数 */
    PageResponse<AgentListItemDto> page(AgentPageQuery query);

    /** Agent 详情：含绑定模型信息与关联资源 id 全量列表 */
    AgentDetailDto getDetail(Long id);

    /** 更新 Agent（整体替换可编辑字段与关联） */
    AgentDto update(Long id, AgentUpdateCmd cmd);

    /** 删除 Agent（逻辑删除，关联同步逻辑删除） */
    void delete(Long id);
}
