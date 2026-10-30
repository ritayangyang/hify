package com.hify.modules.workflow.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.service.ModelConfigService;
import com.hify.modules.workflow.api.dto.WorkflowCreateCmd;
import com.hify.modules.workflow.api.dto.WorkflowDetailDto;
import com.hify.modules.workflow.api.dto.WorkflowDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeInput;
import com.hify.modules.workflow.api.dto.WorkflowListItemDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeInput;
import com.hify.modules.workflow.api.dto.WorkflowOptionDto;
import com.hify.modules.workflow.api.dto.WorkflowPageQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateCmd;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.api.model.NodeConfig;
import com.hify.modules.workflow.api.service.WorkflowService;
import com.hify.modules.workflow.domain.repository.WorkflowEdgeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流管理服务实现。
 * 三张表（workflow/node/edge）的写入始终在同一事务内整体提交或回滚；
 * LLM 节点绑定模型只通过 model 模块的 {@link ModelConfigService} 校验。
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final NodeConfigParser nodeConfigParser;
    private final WorkflowGraphValidator graphValidator;
    private final ModelConfigService modelConfigService;

    @Override
    @Transactional
    public WorkflowDetailDto create(WorkflowCreateCmd cmd) {
        String name = requireName(cmd.name());
        if (workflowRepository.existsByName(name, null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流名称已存在: " + name);
        }
        ParsedGraph graph = parseGraph(cmd.nodes(), cmd.edges());
        graphValidator.validate(graph.nodes(), graph.edges());
        validateLlmModels(graph.nodes());

        WorkflowDto saved = workflowRepository.insert(new WorkflowDto(
                null,
                name,
                cmd.description(),
                cmd.status() == null ? 1 : cmd.status(),
                null,
                null));
        // workflow 已落库拿到 id，再批量写节点/连线；任一失败整体回滚
        workflowNodeRepository.insertBatch(saved.id(), graph.nodes());
        workflowEdgeRepository.insertBatch(saved.id(), graph.edges());
        return assemble(saved.id());
    }

    @Override
    public PageResponse<WorkflowListItemDto> page(WorkflowPageQuery query) {
        PageResponse<WorkflowDto> result = workflowRepository.page(
                query.page(), query.size(), query.name(), query.status());
        List<WorkflowDto> workflows = result.records();
        if (workflows.isEmpty()) {
            return PageResponse.of(List.of(), result.total(), result.page(), result.size());
        }

        // 本页 id 批量装配节点/连线计数：主表 count+page 2 条，计数 2 条，固定 4 条 SQL，无 N+1
        List<Long> workflowIds = workflows.stream().map(WorkflowDto::id).toList();
        Map<Long, Integer> nodeCountMap = workflowNodeRepository.countByWorkflowIds(workflowIds);
        Map<Long, Integer> edgeCountMap = workflowEdgeRepository.countByWorkflowIds(workflowIds);

        List<WorkflowListItemDto> records = workflows.stream()
                .map(workflow -> new WorkflowListItemDto(
                        workflow.id(),
                        workflow.name(),
                        workflow.description(),
                        workflow.status(),
                        nodeCountMap.getOrDefault(workflow.id(), 0),
                        edgeCountMap.getOrDefault(workflow.id(), 0),
                        workflow.createdAt(),
                        workflow.updatedAt()))
                .toList();
        return PageResponse.of(records, result.total(), result.page(), result.size());
    }

    @Override
    public WorkflowDetailDto getDetail(Long id) {
        return assemble(id);
    }

    @Override
    @Transactional
    public WorkflowDetailDto update(Long id, WorkflowUpdateCmd cmd) {
        WorkflowDto existing = getExisting(id);
        String name = StringUtils.hasText(cmd.name()) ? cmd.name().trim() : existing.name();
        if (workflowRepository.existsByName(name, id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流名称已存在: " + name);
        }
        ParsedGraph graph = parseGraph(cmd.nodes(), cmd.edges());
        graphValidator.validate(graph.nodes(), graph.edges());
        validateLlmModels(graph.nodes());

        WorkflowDto merged = new WorkflowDto(
                id,
                name,
                cmd.description(),
                cmd.status() == null ? existing.status() : cmd.status(),
                existing.createdAt(),
                // updatedAt 置空，交给 MetaObjectHandler 在 UPDATE 时自动填充为当前时间
                null);
        workflowRepository.update(merged);

        // 图整体替换：旧节点/连线逻辑删除，再批量插入新的，不做 diff
        workflowEdgeRepository.deleteByWorkflowId(id);
        workflowNodeRepository.deleteByWorkflowId(id);
        workflowNodeRepository.insertBatch(id, graph.nodes());
        workflowEdgeRepository.insertBatch(id, graph.edges());
        return assemble(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getExisting(id);
        workflowEdgeRepository.deleteByWorkflowId(id);
        workflowNodeRepository.deleteByWorkflowId(id);
        workflowRepository.deleteById(id);
    }

    @Override
    public boolean existsEnabled(Long id) {
        return workflowRepository.existsEnabled(id);
    }

    @Override
    public List<WorkflowOptionDto> listEnabledOptions() {
        return workflowRepository.listEnabledOptions();
    }

    private WorkflowDto getExisting(Long id) {
        WorkflowDto workflow = workflowRepository.findById(id);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: " + id);
        }
        return workflow;
    }

    private WorkflowDetailDto assemble(Long id) {
        WorkflowDto workflow = getExisting(id);
        List<WorkflowNodeDto> nodes = workflowNodeRepository.listByWorkflowId(id);
        List<WorkflowEdgeDto> edges = workflowEdgeRepository.listByWorkflowId(id);
        return new WorkflowDetailDto(workflow, nodes, edges);
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "name 不能为空");
        }
        return name.trim();
    }

    /** 原始入参 → 强类型图：节点 config 经 NodeConfigParser 解析，空白 handle 归一为 null */
    private ParsedGraph parseGraph(List<WorkflowNodeInput> nodeInputs, List<WorkflowEdgeInput> edgeInputs) {
        if (nodeInputs == null || nodeInputs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "nodes 不能为空");
        }
        List<WorkflowNodeDto> nodes = nodeInputs.stream().map(this::parseNode).toList();
        List<WorkflowEdgeDto> edges = edgeInputs == null ? List.of() : edgeInputs.stream()
                .map(this::parseEdge)
                .toList();
        return new ParsedGraph(nodes, edges);
    }

    private WorkflowNodeDto parseNode(WorkflowNodeInput input) {
        String nodeId = requireText(input.id(), "节点 id 不能为空");
        NodeType type = NodeType.fromName(input.type());
        NodeConfig config = nodeConfigParser.parseInput(nodeId, type, input.config());
        return new WorkflowNodeDto(nodeId, type, config, input.position());
    }

    private WorkflowEdgeDto parseEdge(WorkflowEdgeInput input) {
        String source = requireText(input.source(), "连线 source 不能为空");
        String target = requireText(input.target(), "连线 target 不能为空");
        String handle = StringUtils.hasText(input.sourceHandle()) ? input.sourceHandle().trim() : null;
        String edgeId = StringUtils.hasText(input.id()) ? input.id().trim() : null;
        return new WorkflowEdgeDto(edgeId, source, handle, target);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    /** 所有 LLM 节点的绑定模型一次性批量校验：存在、已启用、对话模型 */
    private void validateLlmModels(List<WorkflowNodeDto> nodes) {
        record NodeModel(String nodeId, Long modelConfigId) {
        }
        List<NodeModel> refs = nodes.stream()
                .filter(node -> node.config() instanceof LlmNodeConfig)
                .map(node -> {
                    LlmNodeConfig llm = (LlmNodeConfig) node.config();
                    return new NodeModel(node.id(), llm.modelConfigId());
                })
                .toList();
        if (refs.isEmpty()) {
            return;
        }

        List<Long> modelIds = refs.stream().map(NodeModel::modelConfigId).distinct().toList();
        Map<Long, ModelOptionDto> modelMap = modelConfigService.listOptionsByIds(modelIds).stream()
                .collect(Collectors.toMap(ModelOptionDto::id, option -> option, (a, b) -> a));

        for (NodeModel ref : refs) {
            ModelOptionDto model = modelMap.get(ref.modelConfigId());
            if (model == null) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "LLM 节点 " + ref.nodeId() + " 绑定模型不存在: " + ref.modelConfigId());
            }
            if (model.status() == null || model.status() != 1) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "LLM 节点 " + ref.nodeId() + " 绑定模型已停用: " + model.name());
            }
            if (model.modelType() == null || model.modelType() != 1) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "LLM 节点 " + ref.nodeId() + " 绑定模型不是对话模型: " + model.name());
            }
        }
    }

    /** 解析后的图内部载体 */
    private record ParsedGraph(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
    }
}
