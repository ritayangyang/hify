package com.hify.modules.workflow.domain.repository;

import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.api.dto.WorkflowOptionDto;

import java.util.List;

/**
 * 工作流基本信息仓储端口。
 */
public interface WorkflowRepository {

    /** 启用状态 */
    int STATUS_ENABLED = 1;

    WorkflowDto insert(WorkflowDto workflow);

    boolean update(WorkflowDto workflow);

    WorkflowDto findById(Long id);

    /** 指定 id 的工作流是否为启用状态（单条 count，不加载图，供跨模块绑定校验） */
    boolean existsEnabled(Long id);

    /** 全部启用工作流的轻量下拉选项，按 id 升序 */
    List<WorkflowOptionDto> listEnabledOptions();

    /** 同名活跃工作流是否存在（更新时用 excludeId 排除自身） */
    boolean existsByName(String name, Long excludeId);

    PageResponse<WorkflowDto> page(long page, long size, String name, Integer status);

    int deleteById(Long id);
}
