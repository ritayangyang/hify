package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowEdge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流连线 Mapper。
 */
@Mapper
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdge> {
}
