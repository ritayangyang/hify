package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.LlmChatCmd;
import com.hify.modules.model.api.dto.LlmChatResult;
import com.hify.modules.model.api.service.LlmChatService;
import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeRunDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowRunDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.enums.RunStatus;
import com.hify.modules.workflow.api.model.ConditionNodeConfig;
import com.hify.modules.workflow.api.model.EndNodeConfig;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.api.model.StartNodeConfig;
import com.hify.modules.workflow.api.model.ToolNodeConfig;
import com.hify.modules.workflow.domain.engine.executor.ConditionNodeExecutor;
import com.hify.modules.workflow.domain.engine.executor.EndNodeExecutor;
import com.hify.modules.workflow.domain.engine.executor.LlmNodeExecutor;
import com.hify.modules.workflow.domain.engine.executor.StartNodeExecutor;
import com.hify.modules.workflow.domain.repository.WorkflowEdgeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRunRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * WorkflowEngine 单测：执行器/求值器全部用真实实现（LLM 执行器的 chat 服务 mock），
 * 定义仓储用 Mockito，执行记录仓储用手写内存假实现（支持 CAS 收尾与明细回查）。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    private static final Long WF_ID = 1L;

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowNodeRepository workflowNodeRepository;
    @Mock
    private WorkflowEdgeRepository workflowEdgeRepository;
    @Mock
    private LlmChatService llmChatService;

    private FakeRunRepository runRepository;
    private FakeNodeRunRepository nodeRunRepository;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        runRepository = new FakeRunRepository();
        nodeRunRepository = new FakeNodeRunRepository();
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
                new StartNodeExecutor(),
                new LlmNodeExecutor(llmChatService),
                new ConditionNodeExecutor(new ConditionEvaluator()),
                new EndNodeExecutor()));
        engine = new WorkflowEngine(workflowRepository, workflowNodeRepository,
                workflowEdgeRepository, runRepository, nodeRunRepository, registry);
    }

    /* ---------------- 图构造助手 ---------------- */

    private WorkflowNodeDto start(String... inputs) {
        return new WorkflowNodeDto("start", NodeType.START, new StartNodeConfig(List.of(inputs)), null);
    }

    private WorkflowNodeDto llm(String id, long modelId, String prompt, String outputKey) {
        return new WorkflowNodeDto(id, NodeType.LLM,
                new LlmNodeConfig(modelId, prompt, new BigDecimal("0.7"), null, outputKey), null);
    }

    private WorkflowNodeDto end(String replyTemplate) {
        return new WorkflowNodeDto("end", NodeType.END, new EndNodeConfig(replyTemplate), null);
    }

    private WorkflowEdgeDto edge(String source, String target) {
        return new WorkflowEdgeDto(null, source, null, target);
    }

    private WorkflowEdgeDto branchEdge(String source, String handle, String target) {
        return new WorkflowEdgeDto(null, source, handle, target);
    }

    /**
     * 预置一张可执行的图：start → classify → router →(aftersale/presale/default) → 各自答复 → end。
     * END 模板按用例传入（分支只走一条，END 只能引用执行路径上的节点）。
     */
    private List<WorkflowNodeDto> branchGraphNodes(String endTemplate) {
        return List.of(
                start("userMessage"),
                llm("classify", 5, "只返回一个类型词。问题：{{start.userMessage}}", "intent"),
                new WorkflowNodeDto("router", NodeType.CONDITION, new ConditionNodeConfig(List.of(
                        new ConditionNodeConfig.Branch("aftersale", "{{classify.intent}} == '售后'"),
                        new ConditionNodeConfig.Branch("presale", "{{classify.intent}} == '售前'"))), null),
                llm("aftersale", 5, "售后答复：{{start.userMessage}}", "answer"),
                llm("presale", 5, "售前答复：{{start.userMessage}}", "answer"),
                llm("other", 5, "通用答复：{{start.userMessage}}", "answer"),
                end(endTemplate));
    }

    private List<WorkflowEdgeDto> branchGraphEdges(boolean withDefault) {
        List<WorkflowEdgeDto> edges = new ArrayList<>(List.of(
                edge("start", "classify"),
                edge("classify", "router"),
                branchEdge("router", "aftersale", "aftersale"),
                branchEdge("router", "presale", "presale"),
                edge("aftersale", "end"),
                edge("presale", "end"),
                edge("other", "end")));
        if (withDefault) {
            edges.add(branchEdge("router", "default", "other"));
        }
        return edges;
    }

    private void stubEnabledWorkflow() {
        when(workflowRepository.findById(WF_ID))
                .thenReturn(new WorkflowDto(WF_ID, "客服分流", null, 1, LocalDateTime.now(), LocalDateTime.now()));
    }

    private void stubGraph(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        when(workflowNodeRepository.listByWorkflowId(anyLong())).thenReturn(nodes);
        when(workflowEdgeRepository.listByWorkflowId(anyLong())).thenReturn(edges);
    }

    private void stubClassifierReturns(String intent) {
        when(llmChatService.chat(any())).thenAnswer(invocation -> {
            LlmChatCmd cmd = invocation.getArgument(0);
            String prompt = cmd.messages().get(0).content();
            String content = prompt.contains("只返回一个类型词") ? intent : "答复内容";
            return new LlmChatResult(content, 10, 5, 15, 100);
        });
    }

    private WorkflowRunDetailDto execute(Map<String, Object> inputs) {
        return engine.execute(WF_ID, inputs);
    }

    /* ---------------- 用例 ---------------- */

    @Test
    void linearWorkflowSucceedsAndRecordsEverything() {
        stubEnabledWorkflow();
        stubGraph(
                List.of(start("userMessage"),
                        llm("n1", 5, "问题：{{start.userMessage}}", "answer"),
                        end("回复：{{n1.answer}}")),
                List.of(edge("start", "n1"), edge("n1", "end")));
        when(llmChatService.chat(any()))
                .thenReturn(new LlmChatResult("你好", 11, 7, 18, 50));

        WorkflowRunDetailDto detail = execute(Map.of("userMessage", "在吗"));

        assertThat(detail.run().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(detail.run().output()).isEqualTo("回复：你好");
        assertThat(detail.run().totalTokens()).isEqualTo(18);
        assertThat(detail.nodeRuns()).extracting(WorkflowNodeRunDto::nodeId)
                .containsExactly("start", "n1", "end");
        assertThat(detail.nodeRuns()).extracting(WorkflowNodeRunDto::status)
                .containsOnly(RunStatus.SUCCEEDED);
        WorkflowNodeRunDto llmRun = detail.nodeRuns().get(1);
        assertThat(llmRun.promptTokens()).isEqualTo(11);
        assertThat(llmRun.completionTokens()).isEqualTo(7);
        assertThat(llmRun.outputs()).containsEntry("answer", "你好");
    }

    @Test
    void conditionRoutesToMatchedBranchOnly() {
        stubEnabledWorkflow();
        stubGraph(branchGraphNodes("最终：{{aftersale.answer}}"), branchGraphEdges(true));
        stubClassifierReturns("售后");

        WorkflowRunDetailDto detail = execute(Map.of("userMessage", "耳机坏了"));

        assertThat(detail.run().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(detail.run().output()).isEqualTo("最终：答复内容");
        List<String> executed = detail.nodeRuns().stream().map(WorkflowNodeRunDto::nodeId).toList();
        assertThat(executed).contains("router", "aftersale").doesNotContain("presale", "other");
        WorkflowNodeRunDto routerRun = detail.nodeRuns().stream()
                .filter(run -> run.nodeId().equals("router")).findFirst().orElseThrow();
        assertThat(routerRun.outputs()).containsEntry("matchedBranch", "aftersale");
    }

    @Test
    void noBranchMatchedFallsBackToDefaultEdge() {
        stubEnabledWorkflow();
        stubGraph(branchGraphNodes("最终：{{other.answer}}"), branchGraphEdges(true));
        stubClassifierReturns("投诉");

        WorkflowRunDetailDto detail = execute(Map.of("userMessage", "我要投诉"));

        List<String> executed = detail.nodeRuns().stream().map(WorkflowNodeRunDto::nodeId).toList();
        assertThat(executed).contains("router", "other").doesNotContain("aftersale", "presale");
        assertThat(detail.run().output()).isEqualTo("最终：答复内容");
    }

    @Test
    void noBranchMatchedAndNoDefaultFailsRun() {
        stubEnabledWorkflow();
        stubGraph(branchGraphNodes("最终：{{aftersale.answer}}"), branchGraphEdges(false));
        stubClassifierReturns("投诉");

        assertThatThrownBy(() -> execute(Map.of("userMessage", "我要投诉")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("default");

        assertThat(runRepository.single().status()).isEqualTo(RunStatus.FAILED);
        // 条件节点本身执行成功，失败发生在选边阶段
        assertThat(nodeRunRepository.runs(1L)).filteredOn(run -> run.nodeId().equals("router"))
                .singleElement()
                .extracting(WorkflowNodeRunDto::status).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(runRepository.single().errorMessage()).contains("条件节点 router");
    }

    @Test
    void matchedBranchShortCircuitsLaterUnresolvableBranches() {
        // b1 已命中；b2 引用执行路径上不存在的变量。命中即短路，不应再解析 b2
        stubEnabledWorkflow();
        stubGraph(
                List.of(start("userMessage"),
                        new WorkflowNodeDto("router", NodeType.CONDITION, new ConditionNodeConfig(List.of(
                                new ConditionNodeConfig.Branch("b1", "{{start.userMessage}} == '售后'"),
                                new ConditionNodeConfig.Branch("b2", "{{ghost.answer}} == 'x'"))), null),
                        end("已受理：{{start.userMessage}}")),
                List.of(edge("start", "router"), branchEdge("router", "b1", "end")));

        WorkflowRunDetailDto detail = execute(Map.of("userMessage", "售后"));

        assertThat(detail.run().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(detail.run().output()).isEqualTo("已受理：售后");
    }

    @Test
    void noLlmNodeOnPathLeavesTotalTokensNull() {
        stubEnabledWorkflow();
        stubGraph(
                List.of(start("userMessage"), end("收到：{{start.userMessage}}")),
                List.of(edge("start", "end")));

        WorkflowRunDetailDto detail = execute(Map.of("userMessage", "hi"));

        assertThat(detail.run().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(detail.run().totalTokens()).isNull();
    }

    @Test
    void unresolvedVariableFailsNodeAndRun() {
        stubEnabledWorkflow();
        // 走 aftersale 分支，但 END 引用了未执行的 presale.answer
        stubGraph(branchGraphNodes("最终：{{presale.answer}}"), branchGraphEdges(true));
        stubClassifierReturns("售后");

        assertThatThrownBy(() -> execute(Map.of("userMessage", "保修")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("presale.answer");

        assertThat(runRepository.single().status()).isEqualTo(RunStatus.FAILED);
        assertThat(nodeRunRepository.runs(1L)).filteredOn(run -> run.nodeId().equals("end"))
                .singleElement()
                .extracting(WorkflowNodeRunDto::status).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void llmFailureMarksNodeAndRunFailed() {
        stubEnabledWorkflow();
        stubGraph(
                List.of(start("userMessage"),
                        llm("n1", 5, "问题：{{start.userMessage}}", "answer"),
                        end("回复：{{n1.answer}}")),
                List.of(edge("start", "n1"), edge("n1", "end")));
        when(llmChatService.chat(any()))
                .thenThrow(new BizException(com.hify.common.exception.ErrorCode.PARAM_ERROR,
                        "LLM 调用失败(HTTP 401): invalid api key"));

        assertThatThrownBy(() -> execute(Map.of("userMessage", "hi")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("401");

        assertThat(runRepository.single().status()).isEqualTo(RunStatus.FAILED);
        assertThat(nodeRunRepository.runs(1L)).filteredOn(run -> run.nodeId().equals("n1"))
                .singleElement()
                .extracting(WorkflowNodeRunDto::status, WorkflowNodeRunDto::errorMessage)
                .containsExactly(RunStatus.FAILED, "LLM 调用失败(HTTP 401): invalid api key");
        // 后续节点不再执行
        assertThat(nodeRunRepository.runs(1L)).extracting(WorkflowNodeRunDto::nodeId)
                .containsExactly("start", "n1");
    }

    @Test
    void unsupportedNodeTypeFailsClearly() {
        stubEnabledWorkflow();
        stubGraph(
                List.of(start(),
                        new WorkflowNodeDto("tool", NodeType.TOOL, new ToolNodeConfig(9L, null), null),
                        end("{{tool.result}}")),
                List.of(edge("start", "tool"), edge("tool", "end")));

        assertThatThrownBy(() -> execute(Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("TOOL")
                .hasMessageContaining("暂不支持执行");
        assertThat(runRepository.single().status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void cyclicGraphHitsStepLimit() {
        stubEnabledWorkflow();
        // 绕过保存期校验手工构造环：start → a ⇄ b，无 END
        stubGraph(
                List.of(start(),
                        llm("a", 5, "a", "x"),
                        llm("b", 5, "b", "x")),
                List.of(edge("start", "a"), edge("a", "b"), edge("b", "a")));
        when(llmChatService.chat(any()))
                .thenReturn(new LlmChatResult("v", 1, 1, 2, 1));

        assertThatThrownBy(() -> execute(Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("50");
        assertThat(runRepository.single().status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void missingWorkflowThrowsNotFound() {
        when(workflowRepository.findById(WF_ID)).thenReturn(null);

        assertThatThrownBy(() -> execute(Map.of("userMessage", "x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工作流不存在");
    }

    @Test
    void disabledWorkflowRejected() {
        when(workflowRepository.findById(WF_ID))
                .thenReturn(new WorkflowDto(WF_ID, "停用流", null, 0, LocalDateTime.now(), LocalDateTime.now()));

        assertThatThrownBy(() -> execute(Map.of("userMessage", "x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已禁用");
    }

    @Test
    void missingRequiredInputFailsBeforeRunRowCreated() {
        stubEnabledWorkflow();
        // 图会先加载，入参校验在插 RUNNING 之前
        stubGraph(
                List.of(start("userMessage"), llm("n1", 5, "{{start.userMessage}}", "a"), end("{{n1.a}}")),
                List.of(edge("start", "n1"), edge("n1", "end")));

        assertThatThrownBy(() -> execute(Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("userMessage");
        assertThat(runRepository.runs).isEmpty();
        assertThat(nodeRunRepository.rows).isEmpty();
    }

    /* ---------------- 内存假实现 ---------------- */

    private static class FakeRunRepository implements WorkflowRunRepository {
        final Map<Long, WorkflowRunDto> runs = new HashMap<>();
        private long sequence = 0;

        @Override
        public Long insertRunning(Long workflowId, Map<String, Object> inputs) {
            long id = ++sequence;
            runs.put(id, new WorkflowRunDto(id, workflowId, RunStatus.RUNNING,
                    new LinkedHashMap<>(inputs), null, null, 0, null, LocalDateTime.now(), null));
            return id;
        }

        @Override
        public boolean finish(Long runId, RunStatus status, String output, String errorMessage,
                              int elapsedMs, Integer totalTokens) {
            WorkflowRunDto current = runs.get(runId);
            if (current == null || current.status() != RunStatus.RUNNING) {
                return false;
            }
            runs.put(runId, new WorkflowRunDto(current.id(), current.workflowId(), status,
                    current.inputs(), output, errorMessage, elapsedMs, totalTokens,
                    current.createdAt(), LocalDateTime.now()));
            return true;
        }

        @Override
        public WorkflowRunDto findById(Long runId) {
            return runs.get(runId);
        }

        @Override
        public PageResponse<WorkflowRunDto> page(Long workflowId, long page, long size) {
            throw new UnsupportedOperationException();
        }

        WorkflowRunDto single() {
            return runs.values().stream().findFirst().orElseThrow();
        }
    }

    private static class FakeNodeRunRepository implements WorkflowNodeRunRepository {
        private final Map<Long, WorkflowNodeRunDto> rows = new HashMap<>();
        private long sequence = 0;

        @Override
        public Long insertRunning(Long runId, WorkflowNodeDto node) {
            long id = ++sequence;
            rows.put(id, new WorkflowNodeRunDto(id, runId, node.id(), node.type(), RunStatus.RUNNING,
                    null, null, null, 0, null, null, LocalDateTime.now(), null));
            return id;
        }

        @Override
        public boolean markSuccess(Long nodeRunId, Map<String, Object> inputs, Map<String, Object> outputs,
                                   int elapsedMs, Integer promptTokens, Integer completionTokens) {
            WorkflowNodeRunDto current = rows.get(nodeRunId);
            if (current == null || current.status() != RunStatus.RUNNING) {
                return false;
            }
            rows.put(nodeRunId, new WorkflowNodeRunDto(current.id(), current.runId(), current.nodeId(),
                    current.nodeType(), RunStatus.SUCCEEDED, inputs, outputs, null, elapsedMs,
                    promptTokens, completionTokens, current.createdAt(), LocalDateTime.now()));
            return true;
        }

        @Override
        public boolean markFailed(Long nodeRunId, String errorMessage, int elapsedMs) {
            WorkflowNodeRunDto current = rows.get(nodeRunId);
            if (current == null || current.status() != RunStatus.RUNNING) {
                return false;
            }
            rows.put(nodeRunId, new WorkflowNodeRunDto(current.id(), current.runId(), current.nodeId(),
                    current.nodeType(), RunStatus.FAILED, current.inputs(), current.outputs(),
                    errorMessage, elapsedMs, null, null, current.createdAt(), LocalDateTime.now()));
            return true;
        }

        @Override
        public List<WorkflowNodeRunDto> listByRunId(Long runId) {
            return rows.values().stream()
                    .filter(row -> row.runId().equals(runId))
                    .sorted(Comparator.comparingLong(WorkflowNodeRunDto::id))
                    .toList();
        }

        List<WorkflowNodeRunDto> runs(Long runId) {
            return listByRunId(runId);
        }
    }
}
