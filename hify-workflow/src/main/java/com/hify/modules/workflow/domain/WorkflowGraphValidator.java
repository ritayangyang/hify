package com.hify.modules.workflow.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.ConditionNodeConfig;
import com.hify.modules.workflow.api.model.NodeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流图语义校验。JSON 结构合法不等于图合法：
 * 节点 id 唯一、起止节点各一个、连线端点存在、条件分支与 handle 对得上、
 * 无不可达节点、无环路（MVP 为顺序执行，不支持环）。
 */
@Component
public class WorkflowGraphValidator {

    /** 条件节点未命中任何分支时的默认出口 handle */
    public static final String DEFAULT_HANDLE = "default";

    void validate(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流至少包含一个节点");
        }

        Map<String, WorkflowNodeDto> nodeMap = checkNodes(nodes);
        WorkflowNodeDto start = checkStartEnd(nodeMap);
        checkEdges(nodes, nodeMap, edges);

        Map<String, List<WorkflowEdgeDto>> outgoing = outgoingBySource(edges);
        checkReachability(start.id(), nodeMap.keySet(), outgoing);
        checkNoCycle(nodeMap, outgoing);
    }

    /** 节点 id 非空且唯一 */
    private Map<String, WorkflowNodeDto> checkNodes(List<WorkflowNodeDto> nodes) {
        Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
        for (WorkflowNodeDto node : nodes) {
            if (node.id() == null || node.id().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "存在 id 为空的节点");
            }
            if (nodeMap.put(node.id(), node) != null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "节点 id 重复: " + node.id());
            }
        }
        return nodeMap;
    }

    /** 有且仅有一个 START、一个 END */
    private WorkflowNodeDto checkStartEnd(Map<String, WorkflowNodeDto> nodeMap) {
        WorkflowNodeDto start = null;
        int endCount = 0;
        for (WorkflowNodeDto node : nodeMap.values()) {
            if (node.type() == NodeType.START) {
                if (start != null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "开始节点有且只能有一个");
                }
                start = node;
            } else if (node.type() == NodeType.END) {
                endCount++;
            }
        }
        if (start == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少开始节点（START）");
        }
        if (endCount == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少结束节点（END）");
        }
        if (endCount > 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束节点有且只能有一个");
        }
        return start;
    }

    /** 连线端点存在、禁止自连；按起点节点类型校验出线规则 */
    private void checkEdges(List<WorkflowNodeDto> nodes, Map<String, WorkflowNodeDto> nodeMap,
                            List<WorkflowEdgeDto> edges) {
        Set<String> conditionNodeIds = new HashSet<>();
        for (WorkflowNodeDto node : nodes) {
            if (node.type() == NodeType.CONDITION) {
                conditionNodeIds.add(node.id());
            }
        }

        for (WorkflowEdgeDto edge : edges) {
            if (!nodeMap.containsKey(edge.source())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连线起点不存在: " + edge.source());
            }
            if (!nodeMap.containsKey(edge.target())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连线终点不存在: " + edge.target());
            }
            if (edge.source().equals(edge.target())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "节点不能连接自身: " + edge.source());
            }
        }

        Map<String, List<WorkflowEdgeDto>> outgoing = outgoingBySource(edges);
        for (WorkflowNodeDto node : nodes) {
            List<WorkflowEdgeDto> outs = outgoing.getOrDefault(node.id(), List.of());
            if (node.type() == NodeType.CONDITION) {
                checkConditionOutgoing(node, outs);
            } else {
                checkPlainOutgoing(node, outs);
            }
        }
    }

    /** 普通节点：唯一出线且不带 handle；START 必须出线，END 不允许出线 */
    private void checkPlainOutgoing(WorkflowNodeDto node, List<WorkflowEdgeDto> outs) {
        if (node.type() == NodeType.END && !outs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束节点不能有出线: " + node.id());
        }
        if (node.type() != NodeType.END && outs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点缺少出线: " + node.id());
        }
        if (outs.size() > 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非条件节点只能有一条出线: " + node.id());
        }
        for (WorkflowEdgeDto edge : outs) {
            if (edge.sourceHandle() != null) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "非条件节点连线不能带 sourceHandle: " + node.id());
            }
        }
    }

    /** 条件节点：每个 handle 只能一条出线；handle 必须命中已声明分支或 default；每个分支都必须有出线 */
    private void checkConditionOutgoing(WorkflowNodeDto node, List<WorkflowEdgeDto> outs) {
        if (outs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "条件节点缺少出线: " + node.id());
        }
        NodeConfig config = node.config();
        if (!(config instanceof ConditionNodeConfig conditionConfig)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "条件节点配置类型不匹配: " + node.id());
        }
        Set<String> branchIds = new HashSet<>();
        for (ConditionNodeConfig.Branch branch : conditionConfig.branches()) {
            if (!branchIds.add(branch.id())) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "条件节点 " + node.id() + " 的分支 id 重复: " + branch.id());
            }
        }

        Set<String> usedHandles = new HashSet<>();
        boolean hasDefault = false;
        for (WorkflowEdgeDto edge : outs) {
            String handle = edge.sourceHandle();
            if (handle == null || handle.isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "条件节点出线必须指定 sourceHandle: " + node.id());
            }
            if (DEFAULT_HANDLE.equals(handle)) {
                if (hasDefault) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            "条件节点 " + node.id() + " 的 default 出口只能有一条");
                }
                hasDefault = true;
                continue;
            }
            if (!branchIds.contains(handle)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "条件节点 " + node.id() + " 的连线指向了不存在的分支: " + handle);
            }
            if (!usedHandles.add(handle)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "条件分支只能有一条出线: " + node.id() + "/" + handle);
            }
        }
        for (String branchId : branchIds) {
            if (!usedHandles.contains(branchId)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "条件分支没有配置出线: " + node.id() + "/" + branchId);
            }
        }
    }

    /** 从 START 出发所有节点必须可达 */
    private void checkReachability(String startId, Set<String> nodeIds,
                                   Map<String, List<WorkflowEdgeDto>> outgoing) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (WorkflowEdgeDto edge : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(edge.target())) {
                    queue.add(edge.target());
                }
            }
        }
        List<String> unreachable = nodeIds.stream()
                .filter(id -> !visited.contains(id))
                .toList();
        if (!unreachable.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "存在不可达节点: " + String.join(", ", unreachable));
        }
    }

    /** DFS 三色检测：顺序执行模型不允许环路 */
    private void checkNoCycle(Map<String, WorkflowNodeDto> nodeMap,
                              Map<String, List<WorkflowEdgeDto>> outgoing) {
        Set<String> visited = new HashSet<>();
        for (WorkflowNodeDto node : nodeMap.values()) {
            if (visited.contains(node.id())) {
                continue;
            }
            List<String> path = dfsCycle(node.id(), outgoing, visited, new HashSet<>(), new ArrayList<>());
            if (path != null) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "工作流存在环路: " + String.join(" → ", path));
            }
        }
    }

    /**
     * @return 检测到环时返回环路径，无环返回 null
     */
    private List<String> dfsCycle(String current, Map<String, List<WorkflowEdgeDto>> outgoing,
                                  Set<String> visited, Set<String> onStack, List<String> path) {
        visited.add(current);
        onStack.add(current);
        path.add(current);
        for (WorkflowEdgeDto edge : outgoing.getOrDefault(current, List.of())) {
            String next = edge.target();
            if (!onStack.contains(next) && visited.contains(next)) {
                continue;
            }
            if (onStack.contains(next)) {
                path.add(next);
                return path;
            }
            List<String> cycle = dfsCycle(next, outgoing, visited, onStack, path);
            if (cycle != null) {
                return cycle;
            }
        }
        onStack.remove(current);
        path.remove(path.size() - 1);
        return null;
    }

    private Map<String, List<WorkflowEdgeDto>> outgoingBySource(List<WorkflowEdgeDto> edges) {
        Map<String, List<WorkflowEdgeDto>> outgoing = new HashMap<>();
        for (WorkflowEdgeDto edge : edges) {
            outgoing.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(edge);
        }
        return outgoing;
    }
}
