package com.hify.modules.workflow.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流基本信息，对应表 t_workflow。
 * 节点与连线在 t_workflow_node / t_workflow_edge，不内嵌 JSON 大图。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow")
public class Workflow extends BaseEntity {

    /** 工作流名称，活跃记录范围内应用层唯一 */
    private String name;

    /** 工作流简介 */
    private String description;

    /** 状态：0 禁用，1 启用 */
    private Integer status;
}
