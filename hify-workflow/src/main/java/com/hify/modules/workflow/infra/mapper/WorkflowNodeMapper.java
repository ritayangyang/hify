package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流节点 Mapper。
 */
@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}
