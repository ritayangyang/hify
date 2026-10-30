package com.hify.modules.workflow.web;

import com.hify.common.web.PageResponse;
import com.hify.common.web.Result;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunStartCmd;
import com.hify.modules.workflow.api.service.WorkflowRunService;
import com.hify.modules.workflow.web.req.WorkflowRunStartRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流试运行 / 执行记录接口。
 * 与 {@link WorkflowController} 共用 /api/v1/workflows 前缀：
 * /runs/{runId} 为两段路径，不会与 /{id} 冲突。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Validated
public class WorkflowRunController {

    private final WorkflowRunService workflowRunService;

    /** 同步触发一次执行，返回时执行已结束（成功带输出，失败由全局异常处理转错误响应） */
    @PostMapping("/{workflowId}/runs")
    public Result<WorkflowRunDetailDto> start(
            @PathVariable Long workflowId,
            @RequestBody(required = false) WorkflowRunStartRequest request) {
        WorkflowRunStartCmd cmd = new WorkflowRunStartCmd(request == null ? null : request.getInputs());
        return Result.ok(workflowRunService.start(workflowId, cmd));
    }

    /** 某工作流的执行记录分页 */
    @GetMapping("/{workflowId}/runs")
    public Result<PageResponse<WorkflowRunDto>> pageByWorkflow(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.ok(workflowRunService.page(workflowId, page, size));
    }

    /** 执行记录详情（含节点执行明细） */
    @GetMapping("/runs/{runId}")
    public Result<WorkflowRunDetailDto> detail(@PathVariable Long runId) {
        return Result.ok(workflowRunService.getDetail(runId));
    }
}
