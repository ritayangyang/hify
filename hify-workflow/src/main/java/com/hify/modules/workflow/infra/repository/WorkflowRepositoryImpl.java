package com.hify.modules.workflow.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.api.dto.WorkflowOptionDto;
import com.hify.modules.workflow.domain.repository.WorkflowRepository;
import com.hify.modules.workflow.infra.converter.WorkflowPoConverter;
import com.hify.modules.workflow.infra.entity.Workflow;
import com.hify.modules.workflow.infra.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工作流基本信息仓储实现：PO ↔ DTO 转换收口在 infra 层。
 */
@Repository
@RequiredArgsConstructor
public class WorkflowRepositoryImpl implements WorkflowRepository {

    private final WorkflowMapper workflowMapper;
    private final WorkflowPoConverter converter;

    @Override
    public WorkflowDto insert(WorkflowDto workflow) {
        Workflow entity = converter.toEntity(workflow);
        workflowMapper.insert(entity);
        return converter.toDto(entity);
    }

    @Override
    public boolean update(WorkflowDto workflow) {
        return workflowMapper.updateById(converter.toEntity(workflow)) > 0;
    }

    @Override
    public WorkflowDto findById(Long id) {
        Workflow entity = workflowMapper.selectById(id);
        return entity == null ? null : converter.toDto(entity);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return workflowMapper.selectCount(
                        new LambdaQueryWrapper<Workflow>()
                                .eq(Workflow::getName, name)
                                .ne(excludeId != null, Workflow::getId, excludeId))
                > 0;
    }

    @Override
    public boolean existsEnabled(Long id) {
        if (id == null) {
            return false;
        }
        return workflowMapper.selectCount(
                        new LambdaQueryWrapper<Workflow>()
                                .eq(Workflow::getId, id)
                                .eq(Workflow::getStatus, STATUS_ENABLED))
                > 0;
    }

    @Override
    public List<WorkflowOptionDto> listEnabledOptions() {
        // 下拉只需要 id/name，显式 select 两列，不取 MEDIUMTEXT/JSON 大字段
        return workflowMapper.selectList(
                        new LambdaQueryWrapper<Workflow>()
                                .select(Workflow::getId, Workflow::getName)
                                .eq(Workflow::getStatus, STATUS_ENABLED)
                                .orderByAsc(Workflow::getId))
                .stream()
                .map(workflow -> new WorkflowOptionDto(workflow.getId(), workflow.getName()))
                .toList();
    }

    @Override
    public PageResponse<WorkflowDto> page(long page, long size, String name, Integer status) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<Workflow>()
                .like(StringUtils.hasText(name), Workflow::getName, name)
                .eq(status != null, Workflow::getStatus, status)
                .orderByDesc(Workflow::getId);

        Page<Workflow> result = workflowMapper.selectPage(new Page<>(page, size), wrapper);
        List<WorkflowDto> records = result.getRecords().stream()
                .map(converter::toDto)
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public int deleteById(Long id) {
        return workflowMapper.deleteById(id);
    }
}
