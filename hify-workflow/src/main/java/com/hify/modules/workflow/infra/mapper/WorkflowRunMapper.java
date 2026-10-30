package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行记录 Mapper。
 */
@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRun> {
}
