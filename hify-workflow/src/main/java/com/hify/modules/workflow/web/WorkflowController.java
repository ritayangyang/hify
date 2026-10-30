package com.hify.modules.workflow.web;

import com.hify.common.web.PageResponse;
import com.hify.common.web.Result;
import com.hify.modules.workflow.api.dto.WorkflowCreateCmd;
import com.hify.modules.workflow.api.dto.WorkflowDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeInput;
import com.hify.modules.workflow.api.dto.WorkflowListItemDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeInput;
import com.hify.modules.workflow.api.dto.WorkflowOptionDto;
import com.hify.modules.workflow.api.dto.WorkflowPageQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateCmd;
import com.hify.modules.workflow.api.service.WorkflowService;
import com.hify.modules.workflow.web.req.WorkflowCreateRequest;
import com.hify.modules.workflow.web.req.WorkflowPageRequest;
import com.hify.modules.workflow.web.req.WorkflowUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流管理接口。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /** 创建工作流（基本信息 + 完整节点和连线，同事务写三张表） */
    @PostMapping
    public Result<WorkflowDetailDto> create(@Valid @RequestBody WorkflowCreateRequest request) {
        return Result.ok(workflowService.create(toCreateCmd(request)));
    }

    /** 分页查询工作流列表（不含图） */
    @GetMapping
    public Result<PageResponse<WorkflowListItemDto>> page(@Valid WorkflowPageRequest request) {
        WorkflowPageQuery query = new WorkflowPageQuery(
                request.getPage(),
                request.getSize(),
                request.getName(),
                request.getStatus());
        return Result.ok(workflowService.page(query));
    }

    /** 启用工作流下拉选项（id + 名称），供 Agent 绑定使用；字面路径优先于 /{id} */
    @GetMapping("/options")
    public Result<List<WorkflowOptionDto>> options() {
        return Result.ok(workflowService.listEnabledOptions());
    }

    /** 工作流详情：从三张表组装完整节点和连线 */
    @GetMapping("/{id}")
    public Result<WorkflowDetailDto> detail(@PathVariable Long id) {
        return Result.ok(workflowService.getDetail(id));
    }

    /** 更新工作流（整体替换；旧节点/连线逻辑删除后插入新的） */
    @PutMapping("/{id}")
    public Result<WorkflowDetailDto> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowUpdateRequest request) {
        return Result.ok(workflowService.update(id, toUpdateCmd(request)));
    }

    /** 删除工作流（逻辑删除，节点/连线同步逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }

    private WorkflowCreateCmd toCreateCmd(WorkflowCreateRequest request) {
        return new WorkflowCreateCmd(
                request.getName(),
                request.getDescription(),
                request.getStatus(),
                toNodeInputs(request.getNodes()),
                toEdgeInputs(request.getEdges()));
    }

    private WorkflowUpdateCmd toUpdateCmd(WorkflowUpdateRequest request) {
        return new WorkflowUpdateCmd(
                request.getName(),
                request.getDescription(),
                request.getStatus(),
                toNodeInputs(request.getNodes()),
                toEdgeInputs(request.getEdges()));
    }

    private List<WorkflowNodeInput> toNodeInputs(List<WorkflowCreateRequest.NodeRequest> nodes) {
        return nodes.stream()
                .map(node -> new WorkflowNodeInput(
                        node.getId(),
                        node.getType(),
                        node.getConfig(),
                        node.getPosition()))
                .toList();
    }

    private List<WorkflowEdgeInput> toEdgeInputs(List<WorkflowCreateRequest.EdgeRequest> edges) {
        return edges.stream()
                .map(edge -> new WorkflowEdgeInput(
                        edge.getId(),
                        edge.getSource(),
                        edge.getSourceHandle(),
                        edge.getTarget()))
                .toList();
    }
}
