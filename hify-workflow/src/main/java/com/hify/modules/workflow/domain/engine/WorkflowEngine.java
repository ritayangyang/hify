package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.enums.RunStatus;
import com.hify.modules.workflow.api.model.StartNodeConfig;
import com.hify.modules.workflow.domain.WorkflowGraphValidator;
import com.hify.modules.workflow.domain.repository.WorkflowEdgeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRunRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRunRepository;
import com.hify.modules.workflow.domain.engine.executor.EndNodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行引擎：同步图解释器。
 *
 * <p>骨架就是一个 while：从 START 出发，每步取执行器执行、把输出写入变量池、
 * 按连线（CONDITION 用命中分支/default handle）选下一节点，直到 END。
 * <ul>
 *   <li>图结构在保存期已强验（唯一 START/END、无环、可达、handle 完备），
 *       运行时只保留必要的防御性检查（目标缺失、步数上限）；</li>
 *   <li>执行过程跨多次 LLM 调用，不套大事务，run/node_run 的每次状态更新独立提交；</li>
 *   <li>任何节点失败：节点 FAILED → 整次执行 FAILED → 异常向上抛。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    /** 步数兜底：保存期已禁止环，50 步只防御异常数据/死循环 */
    private static final int MAX_STEPS = 50;

    private final WorkflowRepository workflowRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkflowNodeRunRepository workflowNodeRunRepository;
    private final NodeExecutorRegistry executorRegistry;

    /**
     * 同步执行一个工作流。
     *
     * @return 执行详情（含节点执行记录）；失败时先落 FAILED 记录再抛 BizException
     */
    public WorkflowRunDetailDto execute(Long workflowId, Map<String, Object> rawInputs) {
        WorkflowDto workflow = workflowRepository.findById(workflowId);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: " + workflowId);
        }
        if (!Integer.valueOf(1).equals(workflow.status())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流已禁用，无法执行: " + workflow.name());
        }

        List<WorkflowNodeDto> nodes = workflowNodeRepository.listByWorkflowId(workflowId);
        List<WorkflowEdgeDto> edges = workflowEdgeRepository.listByWorkflowId(workflowId);
        Map<String, WorkflowNodeDto> nodeMap = new LinkedHashMap<>();
        for (WorkflowNodeDto node : nodes) {
            nodeMap.put(node.id(), node);
        }
        Map<String, List<WorkflowEdgeDto>> outgoing = new LinkedHashMap<>();
        for (WorkflowEdgeDto edge : edges) {
            outgoing.computeIfAbsent(edge.source(), key -> new java.util.ArrayList<>()).add(edge);
        }
        WorkflowNodeDto startNode = nodes.stream()
                .filter(node -> node.type() == NodeType.START)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_ERROR,
                        "工作流缺少 START 节点: " + workflowId));

        Map<String, Object> inputs = normalizeInputs(rawInputs);
        validateStartInputs(startNode, inputs);

        Long runId = workflowRunRepository.insertRunning(workflowId, inputs);
        ExecutionContext context = new ExecutionContext(inputs);
        long runStartNanos = System.nanoTime();

        String currentNodeId = startNode.id();
        String finalOutput = null;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        boolean pathHasLlmNode = false;
        int steps = 0;

        try {
            while (currentNodeId != null) {
                if (++steps > MAX_STEPS) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR,
                            "执行步数超过 " + MAX_STEPS + "，疑似存在环路，已终止");
                }
                WorkflowNodeDto node = nodeMap.get(currentNodeId);
                if (node == null) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR,
                            "连线指向不存在的节点: " + currentNodeId);
                }

                NodeExecutor executor = executorRegistry.find(node.type())
                        .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR,
                                "节点 " + node.id() + " 的类型 " + node.type()
                                        + " 暂不支持执行（对应模块尚未交付）"));

                Long nodeRunId = workflowNodeRunRepository.insertRunning(runId, node);
                long nodeStartNanos = System.nanoTime();
                NodeExecutorResult result;
                try {
                    result = executor.execute(node, context);
                } catch (Exception e) {
                    int nodeElapsed = elapsedMillis(nodeStartNanos);
                    safeMarkNodeFailed(nodeRunId, e, nodeElapsed);
                    throw e;
                }
                int nodeElapsed = elapsedMillis(nodeStartNanos);

                // START 入参已在执行前预写入变量池，其余节点把输出写入自己的命名空间
                if (node.type() != NodeType.START) {
                    result.outputs().forEach((key, value) -> context.set(node.id(), key, value));
                }
                if (node.type() == NodeType.LLM) {
                    pathHasLlmNode = true;
                }
                if (result.promptTokens() != null) {
                    totalPromptTokens += result.promptTokens();
                }
                if (result.completionTokens() != null) {
                    totalCompletionTokens += result.completionTokens();
                }
                workflowNodeRunRepository.markSuccess(nodeRunId,
                        result.recordedInputs(), result.outputs(), nodeElapsed,
                        result.promptTokens(), result.completionTokens());

                if (node.type() == NodeType.END) {
                    Object reply = result.outputs().get(EndNodeExecutor.REPLY_KEY);
                    finalOutput = reply == null ? null : reply.toString();
                    break;
                }
                currentNodeId = pickNext(node, outgoing.get(node.id()), result.selectedHandle(), nodeMap);
            }

            int elapsedMs = elapsedMillis(runStartNanos);
            Integer totalTokens = pathHasLlmNode ? totalPromptTokens + totalCompletionTokens : null;
            workflowRunRepository.finish(runId, RunStatus.SUCCEEDED, finalOutput, null,
                    elapsedMs, totalTokens);
            log.info("工作流执行成功 workflowId={} runId={} steps={} elapsedMs={} tokens={}",
                    workflowId, runId, steps, elapsedMs, totalTokens);
        } catch (BizException e) {
            finishFailed(runId, e, runStartNanos);
            throw e;
        } catch (Exception e) {
            BizException wrapped = new BizException(ErrorCode.INTERNAL_ERROR,
                    "工作流执行异常: " + e.getMessage(), e);
            finishFailed(runId, wrapped, runStartNanos);
            throw wrapped;
        }

        return assembleDetail(runId);
    }

    private String pickNext(WorkflowNodeDto node, List<WorkflowEdgeDto> nodeOutgoing,
                            String selectedHandle, Map<String, WorkflowNodeDto> nodeMap) {
        if (nodeOutgoing == null || nodeOutgoing.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "节点 " + node.id() + " 没有可执行的后续连线");
        }

        WorkflowEdgeDto next;
        if (node.type() == NodeType.CONDITION) {
            String handle = selectedHandle != null ? selectedHandle : WorkflowGraphValidator.DEFAULT_HANDLE;
            next = nodeOutgoing.stream()
                    .filter(edge -> handle.equals(edge.sourceHandle()))
                    .findFirst()
                    .orElseThrow(() -> {
                        String reason = selectedHandle != null
                                ? "命中的分支 " + selectedHandle + " 没有配置出线"
                                : "所有分支均未命中，且未配置 default 出线";
                        return new BizException(ErrorCode.PARAM_ERROR,
                                "条件节点 " + node.id() + " " + reason);
                    });
        } else {
            // 保存期校验已保证普通节点唯一出线
            next = nodeOutgoing.get(0);
        }

        if (!nodeMap.containsKey(next.target())) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "节点 " + node.id() + " 的连线指向不存在的节点: " + next.target());
        }
        return next.target();
    }

    private Map<String, Object> normalizeInputs(Map<String, Object> rawInputs) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (rawInputs != null) {
            rawInputs.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工作流入参字段名不能为空");
                }
                inputs.put(key.trim(), value);
            });
        }
        return inputs;
    }

    private void validateStartInputs(WorkflowNodeDto startNode, Map<String, Object> inputs) {
        StartNodeConfig config = (StartNodeConfig) startNode.config();
        List<String> missing = config.inputs().stream()
                .filter(name -> !inputs.containsKey(name) || inputs.get(name) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "工作流入参缺少字段: " + String.join(", ", missing));
        }
    }

    private void safeMarkNodeFailed(Long nodeRunId, Exception error, int elapsedMs) {
        try {
            workflowNodeRunRepository.markFailed(nodeRunId, error.getMessage(), elapsedMs);
        } catch (Exception persistError) {
            // 执行记录落库失败只打日志，不掩盖原始执行异常
            log.warn("节点执行记录落 FAILED 失败 nodeRunId={}", nodeRunId, persistError);
        }
    }

    private void finishFailed(Long runId, BizException error, long runStartNanos) {
        try {
            workflowRunRepository.finish(runId, RunStatus.FAILED, null, error.getMessage(),
                    elapsedMillis(runStartNanos), null);
        } catch (Exception persistError) {
            log.warn("执行记录落 FAILED 失败 runId={}", runId, persistError);
        }
    }

    private WorkflowRunDetailDto assembleDetail(Long runId) {
        WorkflowRunDto run = workflowRunRepository.findById(runId);
        List<WorkflowNodeRunDto> nodeRuns = workflowNodeRunRepository.listByRunId(runId);
        return new WorkflowRunDetailDto(run, nodeRuns);
    }

    private int elapsedMillis(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }
}
