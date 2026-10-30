package com.hify.modules.workflow.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunStartCmd;
import com.hify.modules.workflow.api.service.WorkflowRunService;
import com.hify.modules.workflow.domain.engine.WorkflowEngine;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRunRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工作流执行应用服务：线程隔离边界 + 总超时兜底，引擎本身保持同步、无并发感知。
 */
@Slf4j
@Service
public class WorkflowRunServiceImpl implements WorkflowRunService {

    /**
     * 整次执行总超时。工作流含多个 LLM 节点（每个 readTimeout=120s），
     * 因此高于单调用 90s 的兜底，按多节点串行取 180s。
     */
    private static final long RUN_TIMEOUT_SECONDS = 180;

    private final WorkflowEngine workflowEngine;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkflowNodeRunRepository workflowNodeRunRepository;
    private final ThreadPoolExecutor llmExecutor;

    public WorkflowRunServiceImpl(
            WorkflowEngine workflowEngine,
            WorkflowRunRepository workflowRunRepository,
            WorkflowNodeRunRepository workflowNodeRunRepository,
            @Qualifier("llmExecutor") ThreadPoolExecutor llmExecutor) {
        this.workflowEngine = workflowEngine;
        this.workflowRunRepository = workflowRunRepository;
        this.workflowNodeRunRepository = workflowNodeRunRepository;
        this.llmExecutor = llmExecutor;
    }

    @Override
    public WorkflowRunDetailDto start(Long workflowId, WorkflowRunStartCmd cmd) {
        var inputs = cmd == null || cmd.inputs() == null ? java.util.Map.<String, Object>of() : cmd.inputs();
        try {
            // 显式指定线程池（规范 #20）；队列满时 CallerRunsPolicy 形成背压
            return CompletableFuture
                    .supplyAsync(() -> workflowEngine.execute(workflowId, inputs), llmExecutor)
                    .get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 引擎线程在当前 LLM 调用结束后会自行 CAS 收尾；此处先让请求失败
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "工作流执行超时（" + RUN_TIMEOUT_SECONDS + "s），可稍后在执行记录中查看结果");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "工作流执行被中断", e);
        } catch (ExecutionException | CompletionException e) {
            throw unwrap(e);
        }
    }

    @Override
    public WorkflowRunDetailDto getDetail(Long runId) {
        WorkflowRunDto run = workflowRunRepository.findById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "执行记录不存在: " + runId);
        }
        return new WorkflowRunDetailDto(run, workflowNodeRunRepository.listByRunId(runId));
    }

    @Override
    public PageResponse<WorkflowRunDto> page(Long workflowId, long page, long size) {
        return workflowRunRepository.page(workflowId, page, size);
    }

    private BizException unwrap(Throwable wrapper) {
        Throwable cause = wrapper.getCause() != null ? wrapper.getCause() : wrapper;
        if (cause instanceof BizException bizException) {
            return bizException;
        }
        return new BizException(ErrorCode.INTERNAL_ERROR, "工作流执行异常: " + cause.getMessage(), cause);
    }
}
