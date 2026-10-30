package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.Workflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流 Mapper。
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {
}
