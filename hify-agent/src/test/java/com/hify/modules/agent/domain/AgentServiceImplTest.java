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
import com.hify.modules.agent.domain.repository.AgentKnowledgeRepository;
import com.hify.modules.agent.domain.repository.AgentMcpToolRepository;
import com.hify.modules.agent.domain.repository.AgentRepository;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.service.ModelConfigService;
import com.hify.modules.workflow.api.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AgentKnowledgeRepository agentKnowledgeRepository;
    @Mock
    private AgentMcpToolRepository agentMcpToolRepository;
    @Mock
    private ModelConfigService modelConfigService;
    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private AgentServiceImpl agentService;

    private AgentDto agentDto(Long id, Long modelConfigId) {
        return agentDto(id, modelConfigId, null);
    }

    private AgentDto agentDto(Long id, Long modelConfigId, Long workflowId) {
        return new AgentDto(id, "客服 Agent", "简介", "你是客服", modelConfigId,
                Map.of("temperature", 0.7), 1,
                LocalDateTime.of(2026, 10, 13, 10, 0), null, workflowId);
    }

    private ModelOptionDto chatModel(Long id, int modelType, int status) {
        return new ModelOptionDto(id, "豆包 Seed 1.6", "doubao-seed-1-6-250615",
                modelType, status, 5L, "火山方舟");
    }

    private AgentCreateCmd createCmd(String name, Long modelConfigId, List<Long> knowledgeIds, List<Long> mcpToolIds) {
        return createCmd(name, modelConfigId, knowledgeIds, mcpToolIds, null);
    }

    private AgentCreateCmd createCmd(String name, Long modelConfigId, List<Long> knowledgeIds,
                                     List<Long> mcpToolIds, Long workflowId) {
        return new AgentCreateCmd(name, "简介", "你是客服", modelConfigId,
                Map.of("temperature", 0.7), 1, knowledgeIds, mcpToolIds, workflowId);
    }

    @Test
    void createSuccessReplacesRelationsWithDedup() {
        when(agentRepository.existsByName("客服 Agent", null)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(eq(List.of(7L))))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(agentRepository.insert(any())).thenReturn(agentDto(1L, 7L));
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L));

        AgentDto result = agentService.create(
                createCmd("客服 Agent", 7L, Arrays.asList(10L, 10L, null), List.of(20L)));

        assertThat(result.id()).isEqualTo(1L);
        verify(agentKnowledgeRepository).deleteByAgentId(1L);
        verify(agentKnowledgeRepository).insertBatch(1L, List.of(10L));
        verify(agentMcpToolRepository).deleteByAgentId(1L);
        verify(agentMcpToolRepository).insertBatch(1L, List.of(20L));
    }

    @Test
    void createRejectsDuplicateName() {
        when(agentRepository.existsByName("客服 Agent", null)).thenReturn(true);

        assertThatThrownBy(() -> agentService.create(createCmd("客服 Agent", 7L, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
        verify(agentRepository, never()).insert(any());
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void createRejectsMissingModel() {
        when(agentRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> agentService.create(createCmd("客服 Agent", 99L, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("绑定模型不存在");
        verify(agentRepository, never()).insert(any());
    }

    @Test
    void createRejectsDisabledModel() {
        when(agentRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 0)));

        assertThatThrownBy(() -> agentService.create(createCmd("客服 Agent", 7L, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已停用");
        verify(agentRepository, never()).insert(any());
    }

    @Test
    void createRejectsEmbeddingModel() {
        when(agentRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 2, 1)));

        assertThatThrownBy(() -> agentService.create(createCmd("客服 Agent", 7L, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是对话模型");
        verify(agentRepository, never()).insert(any());
    }

    @Test
    void createBindsEnabledWorkflow() {
        when(agentRepository.existsByName("客服 Agent", null)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(eq(List.of(7L))))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(workflowService.existsEnabled(5L)).thenReturn(true);
        when(agentRepository.insert(any())).thenReturn(agentDto(1L, 7L, 5L));
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L, 5L));

        AgentDto result = agentService.create(
                createCmd("客服 Agent", 7L, null, null, 5L));

        assertThat(result.workflowId()).isEqualTo(5L);
        ArgumentCaptor<AgentDto> captor = ArgumentCaptor.forClass(AgentDto.class);
        verify(agentRepository).insert(captor.capture());
        assertThat(captor.getValue().workflowId()).isEqualTo(5L);
    }

    @Test
    void createWithoutWorkflowSkipsWorkflowCheck() {
        when(agentRepository.existsByName("客服 Agent", null)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(eq(List.of(7L))))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(agentRepository.insert(any())).thenReturn(agentDto(1L, 7L));
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L));

        agentService.create(createCmd("客服 Agent", 7L, null, null, null));

        verifyNoInteractions(workflowService);
    }

    @Test
    void createRejectsDisabledOrMissingWorkflow() {
        when(agentRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(workflowService.existsEnabled(99L)).thenReturn(false);

        assertThatThrownBy(() -> agentService.create(createCmd("客服 Agent", 7L, null, null, 99L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("绑定工作流不存在或已停用");
        verify(agentRepository, never()).insert(any());
    }

    @Test
    void updateBindsWorkflowAfterValidation() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        when(agentRepository.findById(1L)).thenReturn(
                new AgentDto(1L, "客服 Agent", null, null, 7L, null, 1, createdAt, createdAt, null));
        when(agentRepository.existsByName("客服 Agent", 1L)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(workflowService.existsEnabled(6L)).thenReturn(true);

        AgentUpdateCmd cmd = new AgentUpdateCmd("客服 Agent", null, null, 7L,
                null, null, null, null, 6L);
        agentService.update(1L, cmd);

        ArgumentCaptor<AgentDto> captor = ArgumentCaptor.forClass(AgentDto.class);
        verify(agentRepository).update(captor.capture());
        assertThat(captor.getValue().workflowId()).isEqualTo(6L);
    }

    @Test
    void updateRejectsDisabledWorkflowAndKeepsOldBinding() {
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L, 5L));
        when(agentRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(workflowService.existsEnabled(99L)).thenReturn(false);

        AgentUpdateCmd cmd = new AgentUpdateCmd("客服 Agent", null, null, 7L,
                null, null, null, null, 99L);
        assertThatThrownBy(() -> agentService.update(1L, cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("绑定工作流不存在或已停用");
        verify(agentRepository, never()).update(any());
    }

    @Test
    void pageAssemblesModelProviderAndRelationCounts() {
        AgentDto a1 = agentDto(1L, 7L);
        AgentDto a2 = agentDto(2L, 8L);
        when(agentRepository.page(1, 10, null, null))
                .thenReturn(PageResponse.of(List.of(a1, a2), 2, 1, 10));
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1), chatModel(8L, 1, 1)));
        when(agentKnowledgeRepository.countByAgentIds(anyCollection()))
                .thenReturn(Map.of(1L, 3));
        when(agentMcpToolRepository.countByAgentIds(anyCollection()))
                .thenReturn(Map.of(2L, 1));

        PageResponse<AgentListItemDto> result =
                agentService.page(new AgentPageQuery(1, 10, null, null));

        assertThat(result.records()).hasSize(2);
        AgentListItemDto first = result.records().get(0);
        assertThat(first.modelName()).isEqualTo("豆包 Seed 1.6");
        assertThat(first.providerName()).isEqualTo("火山方舟");
        assertThat(first.knowledgeCount()).isEqualTo(3);
        assertThat(first.mcpCount()).isZero();
        AgentListItemDto second = result.records().get(1);
        assertThat(second.knowledgeCount()).isZero();
        assertThat(second.mcpCount()).isEqualTo(1);
    }

    @Test
    void emptyPageSkipsAssembly() {
        when(agentRepository.page(1, 10, null, null))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 10));

        PageResponse<AgentListItemDto> result =
                agentService.page(new AgentPageQuery(1, 10, null, null));

        assertThat(result.records()).isEmpty();
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void getDetailAssemblesModelAndRelationIds() {
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L));
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1)));
        when(agentKnowledgeRepository.listIdsByAgentId(1L)).thenReturn(List.of(10L, 11L));
        when(agentMcpToolRepository.listIdsByAgentId(1L)).thenReturn(List.of(20L));

        AgentDetailDto detail = agentService.getDetail(1L);

        assertThat(detail.agent().id()).isEqualTo(1L);
        assertThat(detail.modelName()).isEqualTo("豆包 Seed 1.6");
        assertThat(detail.providerName()).isEqualTo("火山方舟");
        assertThat(detail.knowledgeIds()).containsExactly(10L, 11L);
        assertThat(detail.mcpToolIds()).containsExactly(20L);
    }

    @Test
    void getDetailThrowsNotFound() {
        when(agentRepository.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.getDetail(404L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void updatePreservesCreatedAtClearsUpdatedAtAndReplacesRelations() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        when(agentRepository.findById(1L)).thenReturn(
                new AgentDto(1L, "旧名称", null, null, 7L, null, 1, createdAt, createdAt, 5L));
        when(agentRepository.existsByName("新名称", 1L)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(7L, 1, 1)));

        // cmd.workflowId=null：表单显式提交 null，应解绑旧工作流（旧值 5L 不保留）
        AgentUpdateCmd cmd = new AgentUpdateCmd("新名称", "新简介", "新提示词", 7L,
                null, 0, List.of(30L), List.of(), null);
        agentService.update(1L, cmd);

        ArgumentCaptor<AgentDto> captor = ArgumentCaptor.forClass(AgentDto.class);
        verify(agentRepository).update(captor.capture());
        AgentDto merged = captor.getValue();
        assertThat(merged.createdAt()).isEqualTo(createdAt);
        assertThat(merged.updatedAt()).isNull();
        assertThat(merged.status()).isZero();
        assertThat(merged.workflowId()).isNull();
        verify(agentKnowledgeRepository).insertBatch(1L, List.of(30L));
        verify(agentMcpToolRepository).insertBatch(1L, List.of());
    }

    @Test
    void updateRejectsDuplicateName() {
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L));
        when(agentRepository.existsByName("重名", 1L)).thenReturn(true);

        AgentUpdateCmd cmd = new AgentUpdateCmd("重名", null, null, 7L, null, null, null, null, null);
        assertThatThrownBy(() -> agentService.update(1L, cmd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
        verify(agentRepository, never()).update(any());
    }

    @Test
    void deleteCascadesRelations() {
        when(agentRepository.findById(1L)).thenReturn(agentDto(1L, 7L));

        agentService.delete(1L);

        verify(agentKnowledgeRepository).deleteByAgentId(1L);
        verify(agentMcpToolRepository).deleteByAgentId(1L);
        verify(agentRepository).deleteById(1L);
    }
}
