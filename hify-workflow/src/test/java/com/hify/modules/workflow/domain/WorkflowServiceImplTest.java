package com.hify.modules.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
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
import com.hify.modules.workflow.api.dto.WorkflowPageQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateCmd;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.domain.repository.WorkflowEdgeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowNodeRepository;
import com.hify.modules.workflow.domain.repository.WorkflowRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WorkflowServiceImpl 单测：parser/graphValidator 用真实实现（纯内存），
 * 三个仓储与 model 模块 api 用 Mockito mock。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowNodeRepository workflowNodeRepository;
    @Mock
    private WorkflowEdgeRepository workflowEdgeRepository;
    @Mock
    private ModelConfigService modelConfigService;

    private WorkflowServiceImpl workflowService;

    @BeforeEach
    void setUp() {
        NodeConfigParser parser = new NodeConfigParser(
                MAPPER, Validation.buildDefaultValidatorFactory().getValidator());
        workflowService = new WorkflowServiceImpl(
                workflowRepository, workflowNodeRepository, workflowEdgeRepository,
                parser, new WorkflowGraphValidator(), modelConfigService);
    }

    /* ---------- helpers ---------- */

    private WorkflowNodeInput nodeInput(String id, String type, String configJson) {
        try {
            JsonNode config = configJson == null ? null : MAPPER.readTree(configJson);
            return new WorkflowNodeInput(id, type, config, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WorkflowEdgeInput edgeInput(String source, String target) {
        return new WorkflowEdgeInput(null, source, null, target);
    }

    private WorkflowEdgeInput edgeInput(String source, String handle, String target) {
        return new WorkflowEdgeInput(null, source, handle, target);
    }

    private WorkflowCreateCmd linearCreateCmd(String name) {
        return new WorkflowCreateCmd(name, "简介", 1,
                List.of(
                        nodeInput("start", "START", null),
                        nodeInput("n2", "LLM",
                                "{\"modelConfigId\":6,\"promptTemplate\":\"hi\",\"outputKey\":\"answer\"}"),
                        nodeInput("end", "END", "{\"replyTemplate\":\"{{n2.answer}}\"}")),
                List.of(edgeInput("start", "n2"), edgeInput("n2", "end")));
    }

    private WorkflowUpdateCmd linearUpdateCmd(String name) {
        WorkflowCreateCmd cmd = linearCreateCmd(name);
        return new WorkflowUpdateCmd(cmd.name(), cmd.description(), cmd.status(),
                cmd.nodes(), cmd.edges());
    }

    private WorkflowDto workflowDto(Long id, String name, int status) {
        return new WorkflowDto(id, name, "简介", status,
                LocalDateTime.of(2026, 10, 13, 10, 0), null);
    }

    private ModelOptionDto chatModel(Long id, int modelType, int status) {
        return new ModelOptionDto(id, "豆包 Seed 1.6", "doubao-seed", modelType, status, 5L, "火山方舟");
    }

    private void stubSavedWorkflow(Long id) {
        when(workflowRepository.insert(any())).thenReturn(workflowDto(id, "客服分流", 1));
        when(workflowRepository.findById(id)).thenReturn(workflowDto(id, "客服分流", 1));
        when(workflowNodeRepository.listByWorkflowId(id)).thenReturn(List.of());
        when(workflowEdgeRepository.listByWorkflowId(id)).thenReturn(List.of());
    }

    /* ---------- 创建 ---------- */

    @Test
    @SuppressWarnings("unchecked")
    void createWritesThreeTablesAndParsesNodes() {
        when(workflowRepository.existsByName("客服分流", null)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(6L, 1, 1)));
        stubSavedWorkflow(1L);

        WorkflowDetailDto result = workflowService.create(linearCreateCmd(" 客服分流 "));

        assertThat(result.workflow().id()).isEqualTo(1L);

        ArgumentCaptor<List<WorkflowNodeDto>> nodeCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowNodeRepository).insertBatch(eq(1L), nodeCaptor.capture());
        List<WorkflowNodeDto> savedNodes = nodeCaptor.getValue();
        assertThat(savedNodes).hasSize(3);
        assertThat(savedNodes.stream().map(WorkflowNodeDto::id)).containsExactly("start", "n2", "end");
        assertThat(savedNodes.get(1).config()).isInstanceOf(LlmNodeConfig.class);
        assertThat(((LlmNodeConfig) savedNodes.get(1).config()).modelConfigId()).isEqualTo(6L);

        ArgumentCaptor<List<WorkflowEdgeDto>> edgeCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowEdgeRepository).insertBatch(eq(1L), edgeCaptor.capture());
        assertThat(edgeCaptor.getValue()).hasSize(2);
        assertThat(edgeCaptor.getValue().get(0).sourceHandle()).isNull();
    }

    @Test
    void createRejectsDuplicateName() {
        when(workflowRepository.existsByName("客服分流", null)).thenReturn(true);

        assertThatThrownBy(() -> workflowService.create(linearCreateCmd("客服分流")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工作流名称已存在");

        verify(workflowRepository, never()).insert(any());
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void createRejectsInvalidGraph() {
        // start 重复 → 图校验失败，三张表都不应写入
        WorkflowCreateCmd cmd = new WorkflowCreateCmd("坏图", null, 1,
                List.of(
                        nodeInput("start", "START", null),
                        nodeInput("start", "START", null),
                        nodeInput("end", "END", "{\"replyTemplate\":\"hi\"}")),
                List.of(edgeInput("start", "end")));

        assertThatThrownBy(() -> workflowService.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点 id 重复: start");

        verify(workflowRepository, never()).insert(any());
        verify(workflowNodeRepository, never()).insertBatch(any(), any());
    }

    @Test
    void createRejectsUnknownNodeType() {
        WorkflowCreateCmd cmd = new WorkflowCreateCmd("坏类型", null, 1,
                List.of(
                        nodeInput("start", "START", null),
                        nodeInput("n2", "XXX", "{}"),
                        nodeInput("end", "END", "{\"replyTemplate\":\"hi\"}")),
                List.of(edgeInput("start", "n2"), edgeInput("n2", "end")));

        assertThatThrownBy(() -> workflowService.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知节点类型: XXX");
    }

    @Test
    void createRejectsUnknownLlmModel() {
        when(workflowRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> workflowService.create(linearCreateCmd("客服分流")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("绑定模型不存在: 6");

        verify(workflowRepository, never()).insert(any());
    }

    @Test
    void createRejectsDisabledLlmModel() {
        when(workflowRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(6L, 1, 0)));

        assertThatThrownBy(() -> workflowService.create(linearCreateCmd("客服分流")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("绑定模型已停用");
    }

    @Test
    void createRejectsNonChatLlmModel() {
        when(workflowRepository.existsByName(any(), any())).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(6L, 2, 1)));

        assertThatThrownBy(() -> workflowService.create(linearCreateCmd("客服分流")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是对话模型");
    }

    @Test
    void createWithoutLlmSkipsModelCheck() {
        when(workflowRepository.existsByName("纯模板", null)).thenReturn(false);
        stubSavedWorkflow(2L);

        WorkflowCreateCmd cmd = new WorkflowCreateCmd("纯模板", null, 1,
                List.of(
                        nodeInput("start", "START", null),
                        nodeInput("end", "END", "{\"replyTemplate\":\"{{start.userMessage}}\"}")),
                List.of(edgeInput("start", "end")));

        workflowService.create(cmd);

        verifyNoInteractions(modelConfigService);
    }

    /* ---------- 分页 ---------- */

    @Test
    void pageAssemblesNodeAndEdgeCounts() {
        when(workflowRepository.page(1, 20, null, null)).thenReturn(
                PageResponse.of(List.of(workflowDto(1L, "wf1", 1), workflowDto(2L, "wf2", 1)),
                        2, 1, 20));
        when(workflowNodeRepository.countByWorkflowIds(anyCollection()))
                .thenReturn(Map.of(1L, 3, 2L, 4));
        when(workflowEdgeRepository.countByWorkflowIds(anyCollection()))
                .thenReturn(Map.of(1L, 2, 2L, 5));

        PageResponse<WorkflowListItemDto> result =
                workflowService.page(new WorkflowPageQuery(1, 20, null, null));

        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).nodeCount()).isEqualTo(3);
        assertThat(result.records().get(0).edgeCount()).isEqualTo(2);
        assertThat(result.records().get(1).nodeCount()).isEqualTo(4);
    }

    @Test
    void pageEmptySkipsCountQueries() {
        when(workflowRepository.page(anyLong(), anyLong(), any(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 20));

        PageResponse<WorkflowListItemDto> result =
                workflowService.page(new WorkflowPageQuery(1, 20, null, null));

        assertThat(result.records()).isEmpty();
        verify(workflowNodeRepository, never()).countByWorkflowIds(anyCollection());
        verify(workflowEdgeRepository, never()).countByWorkflowIds(anyCollection());
    }

    /* ---------- 详情 ---------- */

    @Test
    void detailAssemblesFromThreeTables() {
        when(workflowRepository.findById(7L)).thenReturn(workflowDto(7L, "wf", 1));
        when(workflowNodeRepository.listByWorkflowId(7L)).thenReturn(List.of());
        when(workflowEdgeRepository.listByWorkflowId(7L)).thenReturn(List.of());

        WorkflowDetailDto detail = workflowService.getDetail(7L);

        assertThat(detail.workflow().id()).isEqualTo(7L);
        assertThat(detail.nodes()).isEmpty();
        assertThat(detail.edges()).isEmpty();
    }

    @Test
    void detailNotFound() {
        when(workflowRepository.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getDetail(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工作流不存在: 404");
    }

    /* ---------- 更新 ---------- */

    @Test
    @SuppressWarnings("unchecked")
    void updateSoftDeletesOldGraphAndInsertsNew() {
        when(workflowRepository.findById(9L)).thenReturn(workflowDto(9L, "旧名称", 0));
        when(workflowRepository.existsByName("新名称", 9L)).thenReturn(false);
        when(modelConfigService.listOptionsByIds(anyCollection()))
                .thenReturn(List.of(chatModel(6L, 1, 1)));
        when(workflowNodeRepository.listByWorkflowId(9L)).thenReturn(List.of());
        when(workflowEdgeRepository.listByWorkflowId(9L)).thenReturn(List.of());

        WorkflowUpdateCmd cmd = new WorkflowUpdateCmd("新名称", null, null,
                List.of(
                        nodeInput("start", "START", null),
                        nodeInput("n3", "LLM",
                                "{\"modelConfigId\":6,\"promptTemplate\":\"new\",\"outputKey\":\"out\"}"),
                        nodeInput("end", "END", "{\"replyTemplate\":\"{{n3.out}}\"}")),
                List.of(edgeInput("start", "n3"), edgeInput("n3", "end")));

        workflowService.update(9L, cmd);

        // 基本信息更新：名称合并、status 为空保留旧值 0、createdAt 保留、updatedAt 置空触发填充
        ArgumentCaptor<WorkflowDto> dtoCaptor = ArgumentCaptor.forClass(WorkflowDto.class);
        verify(workflowRepository).update(dtoCaptor.capture());
        WorkflowDto merged = dtoCaptor.getValue();
        assertThat(merged.id()).isEqualTo(9L);
        assertThat(merged.name()).isEqualTo("新名称");
        assertThat(merged.status()).isZero();
        assertThat(merged.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        assertThat(merged.updatedAt()).isNull();

        // 旧图先逻辑删除（先边后节点），再批量插新图，不做 diff
        InOrder inOrder = inOrder(workflowEdgeRepository, workflowNodeRepository);
        inOrder.verify(workflowEdgeRepository).deleteByWorkflowId(9L);
        inOrder.verify(workflowNodeRepository).deleteByWorkflowId(9L);

        ArgumentCaptor<List<WorkflowNodeDto>> nodeCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowNodeRepository).insertBatch(eq(9L), nodeCaptor.capture());
        assertThat(nodeCaptor.getValue().stream().map(WorkflowNodeDto::id))
                .containsExactly("start", "n3", "end");
    }

    @Test
    void updateNotFound() {
        when(workflowRepository.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> workflowService.update(404L, linearUpdateCmd("x")))
                .isInstanceOf(BizException.class);

        verify(workflowRepository, never()).update(any());
    }

    @Test
    void updateRejectsDuplicateName() {
        when(workflowRepository.findById(9L)).thenReturn(workflowDto(9L, "旧名称", 1));
        when(workflowRepository.existsByName("别人的名称", 9L)).thenReturn(true);

        assertThatThrownBy(() -> workflowService.update(9L, linearUpdateCmd("别人的名称")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工作流名称已存在");

        verify(workflowRepository, never()).update(any());
        verify(workflowNodeRepository, never()).deleteByWorkflowId(9L);
    }

    /* ---------- 删除 ---------- */

    @Test
    void deleteCascadesEdgesNodesWorkflowInOrder() {
        when(workflowRepository.findById(9L)).thenReturn(workflowDto(9L, "wf", 1));

        workflowService.delete(9L);

        InOrder inOrder = inOrder(workflowEdgeRepository, workflowNodeRepository, workflowRepository);
        inOrder.verify(workflowEdgeRepository).deleteByWorkflowId(9L);
        inOrder.verify(workflowNodeRepository).deleteByWorkflowId(9L);
        inOrder.verify(workflowRepository).deleteById(9L);
    }

    @Test
    void deleteNotFoundDoesNothing() {
        when(workflowRepository.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> workflowService.delete(404L))
                .isInstanceOf(BizException.class);

        verify(workflowRepository, never()).deleteById(404L);
        verify(workflowNodeRepository, never()).deleteByWorkflowId(404L);
    }
}
