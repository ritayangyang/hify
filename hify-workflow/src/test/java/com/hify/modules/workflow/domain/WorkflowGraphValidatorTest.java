package com.hify.modules.workflow.domain;

import com.hify.common.exception.BizException;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.ConditionNodeConfig;
import com.hify.modules.workflow.api.model.EndNodeConfig;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.api.model.NodeConfig;
import com.hify.modules.workflow.api.model.StartNodeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工作流图语义校验单测。
 */
class WorkflowGraphValidatorTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    /* ---------- helpers ---------- */

    private WorkflowNodeDto start() {
        return node("start", NodeType.START, new StartNodeConfig(List.of("userMessage")));
    }

    private WorkflowNodeDto end() {
        return node("end", NodeType.END, new EndNodeConfig("{{n2.answer}}"));
    }

    private WorkflowNodeDto llm(String id) {
        return node(id, NodeType.LLM,
                new LlmNodeConfig(6L, "你是客服", null, null, "answer"));
    }

    private WorkflowNodeDto condition(String id, String... branchIds) {
        List<ConditionNodeConfig.Branch> branches = new ArrayList<>();
        for (String branchId : branchIds) {
            branches.add(new ConditionNodeConfig.Branch(branchId, "{{x}} == 1"));
        }
        return node(id, NodeType.CONDITION, new ConditionNodeConfig(branches));
    }

    private WorkflowNodeDto node(String id, NodeType type, NodeConfig config) {
        return new WorkflowNodeDto(id, type, config, null);
    }

    private WorkflowEdgeDto edge(String source, String target) {
        return new WorkflowEdgeDto(null, source, null, target);
    }

    private WorkflowEdgeDto edge(String source, String handle, String target) {
        return new WorkflowEdgeDto(null, source, handle, target);
    }

    private List<WorkflowNodeDto> nodes(WorkflowNodeDto... nodes) {
        return List.of(nodes);
    }

    private List<WorkflowEdgeDto> edges(WorkflowEdgeDto... edges) {
        return List.of(edges);
    }

    /* ---------- 合法图 ---------- */

    @Test
    void validLinearGraphPasses() {
        assertThatCode(() -> validator.validate(
                nodes(start(), llm("n2"), end()),
                edges(edge("start", "n2"), edge("n2", "end"))))
                .doesNotThrowAnyException();
    }

    @Test
    void validConditionGraphWithDefaultPasses() {
        // start → c1；b1 → n2 → end；default → end
        assertThatCode(() -> validator.validate(
                nodes(start(), condition("c1", "b1"), llm("n2"), end()),
                edges(edge("start", "c1"),
                        edge("c1", "b1", "n2"),
                        edge("n2", "end"),
                        edge("c1", WorkflowGraphValidator.DEFAULT_HANDLE, "end"))))
                .doesNotThrowAnyException();
    }

    /* ---------- 节点问题 ---------- */

    @Test
    void rejectEmptyNodes() {
        assertThatThrownBy(() -> validator.validate(List.of(), List.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少包含一个节点");
    }

    @Test
    void rejectDuplicateNodeId() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("x"), llm("x"), end()),
                edges(edge("start", "x"), edge("x", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点 id 重复: x");
    }

    @Test
    void rejectMissingStart() {
        assertThatThrownBy(() -> validator.validate(
                nodes(llm("n2"), end()),
                edges(edge("n2", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少开始节点");
    }

    @Test
    void rejectTwoStarts() {
        WorkflowNodeDto secondStart = node("start2", NodeType.START, new StartNodeConfig(List.of()));
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), secondStart, llm("n2"), end()),
                edges(edge("start", "n2"), edge("n2", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始节点有且只能有一个");
    }

    @Test
    void rejectMissingEnd() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2")),
                edges(edge("start", "n2"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少结束节点");
    }

    /* ---------- 连线问题 ---------- */

    @Test
    void rejectDanglingEdgeTarget() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), end()),
                edges(edge("start", "n2"), edge("n2", "ghost"), edge("n2", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("连线终点不存在: ghost");
    }

    @Test
    void rejectSelfLoop() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), end()),
                edges(edge("start", "n2"), edge("n2", "n2"), edge("n2", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能连接自身");
    }

    @Test
    void rejectNodeWithoutOutgoing() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), end()),
                edges(edge("start", "n2"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点缺少出线: n2");
    }

    @Test
    void rejectEndWithOutgoing() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), end()),
                edges(edge("start", "n2"), edge("n2", "end"), edge("end", "n2"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("结束节点不能有出线");
    }

    @Test
    void rejectPlainNodeWithTwoOutgoing() {
        WorkflowNodeDto n3 = llm("n3");
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), n3, end()),
                edges(edge("start", "n2"), edge("n2", "n3"), edge("n2", "end"), edge("n3", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非条件节点只能有一条出线: n2");
    }

    /* ---------- 条件分支问题 ---------- */

    @Test
    void rejectBranchWithoutEdge() {
        // 声明了 b1/b2，但 b2 没有出线
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), condition("c1", "b1", "b2"), llm("n2"), end()),
                edges(edge("start", "c1"),
                        edge("c1", "b1", "n2"),
                        edge("n2", "end"),
                        edge("c1", WorkflowGraphValidator.DEFAULT_HANDLE, "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件分支没有配置出线: c1/b2");
    }

    @Test
    void rejectEdgeToUnknownBranch() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), condition("c1", "b1"), llm("n2"), end()),
                edges(edge("start", "c1"),
                        edge("c1", "bX", "n2"),
                        edge("n2", "end"),
                        edge("c1", WorkflowGraphValidator.DEFAULT_HANDLE, "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在的分支: bX");
    }

    @Test
    void rejectDuplicateHandleEdge() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), condition("c1", "b1"), llm("n2"), llm("n3"), end()),
                edges(edge("start", "c1"),
                        edge("c1", "b1", "n2"),
                        edge("c1", "b1", "n3"),
                        edge("n2", "end"),
                        edge("n3", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件分支只能有一条出线");
    }

    @Test
    void rejectConditionEdgeWithoutHandle() {
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), condition("c1", "b1"), llm("n2"), end()),
                edges(edge("start", "c1"), edge("c1", "n2"), edge("n2", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须指定 sourceHandle");
    }

    /* ---------- 可达性 / 环路 ---------- */

    @Test
    void rejectUnreachableNode() {
        // orphan 自身出线合法（接到 end），但没有任何节点通向它 → 不可达
        WorkflowNodeDto orphan = llm("orphan");
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), llm("n2"), orphan, end()),
                edges(edge("start", "n2"), edge("n2", "end"), edge("orphan", "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("存在不可达节点: orphan");
    }

    @Test
    void rejectCycle() {
        // start → c1；b1 → n2 → c1（回环）；default → end
        assertThatThrownBy(() -> validator.validate(
                nodes(start(), condition("c1", "b1"), llm("n2"), end()),
                edges(edge("start", "c1"),
                        edge("c1", "b1", "n2"),
                        edge("n2", "c1"),
                        edge("c1", WorkflowGraphValidator.DEFAULT_HANDLE, "end"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工作流存在环路");
    }
}
