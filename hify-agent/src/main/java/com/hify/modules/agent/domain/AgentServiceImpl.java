package com.hify.modules.agent.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.modules.agent.api.dto.AgentCreateCmd;
import com.hify.modules.agent.api.dto.AgentDetailDto;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.api.dto.AgentListItemDto;
import com.hify.modules.agent.api.dto.AgentPageQuery;
import com.hify.modules.agent.api.dto.AgentUpdateCmd;
import com.hify.modules.agent.api.service.AgentService;
import com.hify.modules.agent.domain.repository.AgentKnowledgeRepository;
import com.hify.modules.agent.domain.repository.AgentMcpToolRepository;
import com.hify.modules.agent.domain.repository.AgentRepository;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.service.ModelConfigService;
import com.hify.modules.workflow.api.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Agent 管理服务实现。
 * 跨模块模型信息只通过 model 模块的 {@link ModelConfigService} 获取，
 * 工作流信息只通过 workflow 模块的 {@link WorkflowService} 获取。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final AgentKnowledgeRepository agentKnowledgeRepository;
    private final AgentMcpToolRepository agentMcpToolRepository;
    private final ModelConfigService modelConfigService;
    private final WorkflowService workflowService;

    @Override
    @Transactional
    public AgentDto create(AgentCreateCmd cmd) {
        String name = cmd.name().trim();
        if (agentRepository.existsByName(name, null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在: " + name);
        }
        requireEnabledChatModel(cmd.modelConfigId());
        requireEnabledWorkflow(cmd.workflowId());

        AgentDto created = agentRepository.insert(new AgentDto(
                null,
                name,
                cmd.description(),
                cmd.systemPrompt(),
                cmd.modelConfigId(),
                cmd.modelParams(),
                cmd.status() == null ? 1 : cmd.status(),
                null,
                null,
                cmd.workflowId()));
        replaceRelations(created.id(), cmd.knowledgeIds(), cmd.mcpToolIds());
        return agentRepository.findById(created.id());
    }

    @Override
    public PageResponse<AgentListItemDto> page(AgentPageQuery query) {
        PageResponse<AgentDto> result = agentRepository.page(
                query.page(), query.size(), query.name(), query.status());
        List<AgentDto> agents = result.records();
        if (agents.isEmpty()) {
            return PageResponse.of(List.of(), result.total(), result.page(), result.size());
        }

        // 本页 id 批量装配模型摘要与关联计数：Agent 1 条 + 模型 1 条 + 关联 2 条，固定 4 条 SQL，无 N+1
        List<Long> agentIds = agents.stream().map(AgentDto::id).toList();
        List<Long> modelConfigIds = agents.stream().map(AgentDto::modelConfigId).distinct().toList();
        Map<Long, ModelOptionDto> modelMap = modelConfigService.listOptionsByIds(modelConfigIds).stream()
                .collect(Collectors.toMap(ModelOptionDto::id, option -> option, (a, b) -> a));
        Map<Long, Integer> knowledgeCountMap = agentKnowledgeRepository.countByAgentIds(agentIds);
        Map<Long, Integer> mcpCountMap = agentMcpToolRepository.countByAgentIds(agentIds);

        List<AgentListItemDto> records = agents.stream()
                .map(agent -> toListItem(agent,
                        modelMap.get(agent.modelConfigId()),
                        knowledgeCountMap.getOrDefault(agent.id(), 0),
                        mcpCountMap.getOrDefault(agent.id(), 0)))
                .toList();
        return PageResponse.of(records, result.total(), result.page(), result.size());
    }

    private AgentListItemDto toListItem(AgentDto agent, ModelOptionDto model, int knowledgeCount, int mcpCount) {
        return new AgentListItemDto(
                agent.id(),
                agent.name(),
                agent.description(),
                agent.systemPrompt(),
                agent.modelConfigId(),
                agent.modelParams(),
                agent.status(),
                agent.createdAt(),
                agent.updatedAt(),
                model == null ? null : model.name(),
                model == null ? null : model.providerName(),
                knowledgeCount,
                mcpCount);
    }

    @Override
    public AgentDetailDto getDetail(Long id) {
        AgentDto agent = getExisting(id);
        ModelOptionDto model = findModelOption(agent.modelConfigId());
        return new AgentDetailDto(
                agent,
                model == null ? null : model.name(),
                model == null ? null : model.providerName(),
                agentKnowledgeRepository.listIdsByAgentId(id),
                agentMcpToolRepository.listIdsByAgentId(id));
    }

    @Override
    @Transactional
    public AgentDto update(Long id, AgentUpdateCmd cmd) {
        AgentDto existing = getExisting(id);
        String name = StringUtils.hasText(cmd.name()) ? cmd.name().trim() : existing.name();
        if (agentRepository.existsByName(name, id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在: " + name);
        }
        requireEnabledChatModel(cmd.modelConfigId() == null ? existing.modelConfigId() : cmd.modelConfigId());
        // workflowId 由表单显式提交：null 即解绑，不做“null 保留旧值”
        requireEnabledWorkflow(cmd.workflowId());

        AgentDto merged = new AgentDto(
                id,
                name,
                cmd.description(),
                cmd.systemPrompt(),
                cmd.modelConfigId() == null ? existing.modelConfigId() : cmd.modelConfigId(),
                cmd.modelParams() == null ? existing.modelParams() : cmd.modelParams(),
                cmd.status() == null ? existing.status() : cmd.status(),
                existing.createdAt(),
                // updatedAt 置空，交给 MetaObjectHandler 在 UPDATE 时自动填充为当前时间
                null,
                cmd.workflowId());
        agentRepository.update(merged);
        replaceRelations(id, cmd.knowledgeIds(), cmd.mcpToolIds());
        return agentRepository.findById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getExisting(id);
        agentKnowledgeRepository.deleteByAgentId(id);
        agentMcpToolRepository.deleteByAgentId(id);
        agentRepository.deleteById(id);
    }

    private AgentDto getExisting(Long id) {
        AgentDto agent = agentRepository.findById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: " + id);
        }
        return agent;
    }

    private ModelOptionDto findModelOption(Long modelConfigId) {
        return modelConfigService.listOptionsByIds(List.of(modelConfigId)).stream()
                .findFirst()
                .orElse(null);
    }

    /** 绑定工作流时要求工作流存在且为启用状态；workflowId 为 null 表示普通对话，不校验 */
    private void requireEnabledWorkflow(Long workflowId) {
        if (workflowId == null) {
            return;
        }
        if (!workflowService.existsEnabled(workflowId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定工作流不存在或已停用: " + workflowId);
        }
    }

    /** 创建/绑定场景要求模型存在、已启用且为对话模型 */
    private void requireEnabledChatModel(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "modelConfigId 不能为空");
        }
        ModelOptionDto model = findModelOption(modelConfigId);
        if (model == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定模型不存在: " + modelConfigId);
        }
        if (model.status() == null || model.status() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定模型已停用: " + model.name());
        }
        if (model.modelType() == null || model.modelType() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定模型不是对话模型: " + model.name());
        }
    }

    /** 关联整体替换：旧关联逻辑删除，新关联去重后插入 */
    private void replaceRelations(Long agentId, List<Long> knowledgeIds, List<Long> mcpToolIds) {
        List<Long> distinctKnowledgeIds = distinctIds(knowledgeIds);
        List<Long> distinctMcpToolIds = distinctIds(mcpToolIds);
        agentKnowledgeRepository.deleteByAgentId(agentId);
        agentKnowledgeRepository.insertBatch(agentId, distinctKnowledgeIds);
        agentMcpToolRepository.deleteByAgentId(agentId);
        agentMcpToolRepository.insertBatch(agentId, distinctMcpToolIds);
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
