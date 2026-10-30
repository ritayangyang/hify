package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowNodeRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点执行记录 Mapper。
 */
@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRun> {
}
