package com.hify.modules.workflow.api.service;

import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowCreateCmd;
import com.hify.modules.workflow.api.dto.WorkflowDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowListItemDto;
import com.hify.modules.workflow.api.dto.WorkflowOptionDto;
import com.hify.modules.workflow.api.dto.WorkflowPageQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateCmd;

import java.util.List;

/**
 * 工作流管理服务（其他模块只能依赖本接口）。
 */
public interface WorkflowService {

    /**
     * 创建工作流：基本信息写一条、节点/连线批量写入，三张表同一事务。
     */
    WorkflowDetailDto create(WorkflowCreateCmd cmd);

    /** 分页查询工作流列表（不含图） */
    PageResponse<WorkflowListItemDto> page(WorkflowPageQuery query);

    /** 查询详情：从三张表组装完整节点和连线 */
    WorkflowDetailDto getDetail(Long id);

    /**
     * 更新工作流：基本信息整体替换，旧节点/连线逻辑删除后批量插入新的，
     * 不做 diff，三张表同一事务。
     */
    WorkflowDetailDto update(Long id, WorkflowUpdateCmd cmd);

    /** 逻辑删除工作流（节点/连线同步逻辑删除） */
    void delete(Long id);

    /** 指定 id 的工作流是否存在且启用（供 Agent 绑定校验，单条 count 不拉图） */
    boolean existsEnabled(Long id);

    /** 全部启用工作流的下拉选项（id + 名称） */
    List<WorkflowOptionDto> listEnabledOptions();
}
