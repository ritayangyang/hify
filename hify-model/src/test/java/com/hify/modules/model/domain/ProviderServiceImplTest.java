package com.hify.modules.model.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.dto.ProviderCreateCmd;
import com.hify.modules.model.api.dto.ProviderDetailDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.dto.ProviderHealthDto;
import com.hify.modules.model.api.dto.ProviderListItemDto;
import com.hify.modules.model.api.dto.ProviderUpdateCmd;
import com.hify.modules.model.api.enums.ProviderHealthStatus;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.domain.repository.ProviderHealthRepository;
import com.hify.modules.model.domain.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceImplTest {

    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ModelConfigRepository modelConfigRepository;
    @Mock
    private ProviderHealthRepository healthRepository;
    @Mock
    private ProviderConnectionTester connectionTester;

    @InjectMocks
    private ProviderServiceImpl providerService;

    private ProviderDto providerDto(Long id) {
        return new ProviderDto(id, "openai", ProviderType.OPENAI, "OpenAI",
                "https://api.openai.com", Map.of("apiKey", "sk-secret"), 1, null, null);
    }

    @Test
    void createRejectsDuplicatedCode() {
        when(providerRepository.existsByCode("openai")).thenReturn(true);

        ProviderCreateCmd cmd = new ProviderCreateCmd(
                "openai", ProviderType.OPENAI, "OpenAI", "https://api.openai.com",
                Map.of("apiKey", "sk-secret"), 1);

        assertThatThrownBy(() -> providerService.create(cmd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
        // 查重即止：不写提供商表，也不得初始化健康行
        verify(providerRepository, never()).insert(any());
        verifyNoInteractions(healthRepository);
    }

    @Test
    void createMasksApiKeyAndInitializesHealth() {
        when(providerRepository.existsByCode("openai")).thenReturn(false);
        when(providerRepository.insert(any())).thenReturn(providerDto(1L));

        ProviderDto result = providerService.create(new ProviderCreateCmd(
                "openai", ProviderType.OPENAI, "OpenAI", "https://api.openai.com",
                Map.of("apiKey", "sk-secret"), null));

        assertThat(result.authConfig()).isEqualTo(Map.of("apiKey", "******"));
        verify(healthRepository).saveOrUpdate(
                new ProviderHealthDto(1L, 0, null, null));
    }

    @Test
    void createDerivesCodeFromTypeWhenAbsent() {
        when(providerRepository.existsByCode("openai")).thenReturn(false);
        when(providerRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderDto result = providerService.create(new ProviderCreateCmd(
                null, ProviderType.OPENAI, "OpenAI", "https://api.openai.com",
                Map.of("apiKey", "sk-x"), null));

        assertThat(result.code()).isEqualTo("openai");
        ArgumentCaptor<ProviderDto> captor = ArgumentCaptor.forClass(ProviderDto.class);
        verify(providerRepository).insert(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("openai");
    }

    @Test
    void createWithoutAuthConfigNormalizesToEmptyMap() {
        when(providerRepository.existsByCode("ollama")).thenReturn(false);
        when(providerRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        providerService.create(new ProviderCreateCmd(
                "ollama", ProviderType.OLLAMA, "Ollama",
                "http://localhost:11434", null, null));

        ArgumentCaptor<ProviderDto> captor = ArgumentCaptor.forClass(ProviderDto.class);
        verify(providerRepository).insert(captor.capture());
        assertThat(captor.getValue().authConfig()).isEmpty();
    }

    @Test
    void should_persistRawKeyAndDefaultStatusAndReturnMaskedDto_when_createSucceeds() {
        // Given：code 唯一、status 缺省、authConfig 携带明文 apiKey；仓储回填自增 id
        when(providerRepository.existsByCode("openai-backup")).thenReturn(false);
        when(providerRepository.insert(any())).thenAnswer(inv -> {
            ProviderDto toInsert = inv.getArgument(0);
            return new ProviderDto(1L, toInsert.code(), toInsert.type(), toInsert.name(),
                    toInsert.baseUrl(), toInsert.authConfig(), toInsert.status(), null, null);
        });
        ProviderCreateCmd cmd = new ProviderCreateCmd(
                "openai-backup", ProviderType.OPENAI, "OpenAI Backup",
                "https://api.openai.com", Map.of("apiKey", "sk-test-xxx"), null);

        // When
        ProviderDto result = providerService.create(cmd);

        // Then：出参带回填 id，apiKey 脱敏且不含明文
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.authConfig()).isEqualTo(Map.of("apiKey", "******"));

        // 落库实体：明文 key 原样持久化、status 缺省归一化为 1、id 留空由数据库回填
        ArgumentCaptor<ProviderDto> providerCaptor = ArgumentCaptor.forClass(ProviderDto.class);
        verify(providerRepository).insert(providerCaptor.capture());
        ProviderDto persisted = providerCaptor.getValue();
        assertThat(persisted.id()).isNull();
        assertThat(persisted.code()).isEqualTo("openai-backup");
        assertThat(persisted.type()).isEqualTo(ProviderType.OPENAI);
        assertThat(persisted.name()).isEqualTo("OpenAI Backup");
        assertThat(persisted.baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(persisted.status()).isEqualTo(1);
        assertThat(persisted.authConfig()).isEqualTo(Map.of("apiKey", "sk-test-xxx"));

        // 同一事务内初始化健康行（失败计数 0、无延迟、无成功时间）
        ArgumentCaptor<ProviderHealthDto> healthCaptor =
                ArgumentCaptor.forClass(ProviderHealthDto.class);
        verify(healthRepository).saveOrUpdate(healthCaptor.capture());
        assertThat(healthCaptor.getValue())
                .isEqualTo(new ProviderHealthDto(1L, 0, null, null));

        // 调用顺序：查重 → 落库 → 健康行
        InOrder inOrder = inOrder(providerRepository, healthRepository);
        inOrder.verify(providerRepository).existsByCode("openai-backup");
        inOrder.verify(providerRepository).insert(any());
        inOrder.verify(healthRepository).saveOrUpdate(any());
    }

    @Test
    void pageMasksApiKeyAndAssemblesHealthAndModelCount() {
        when(providerRepository.page(1, 20, null, null, null))
                .thenReturn(PageResponse.of(List.of(providerDto(1L), providerDto(2L)), 2, 1, 20));
        when(healthRepository.listByProviderIds(any()))
                .thenReturn(List.of(new ProviderHealthDto(1L, 0, 42,
                        LocalDateTime.of(2026, 10, 13, 10, 0))));
        when(modelConfigRepository.countEnabledByProviderIds(any()))
                .thenReturn(Map.of(1L, 3));

        PageResponse<ProviderListItemDto> result = providerService.page(
                new com.hify.modules.model.api.dto.ProviderPageQuery(1, 20, null, null, null));

        assertThat(result.records()).hasSize(2);
        ProviderListItemDto first = result.records().get(0);
        assertThat(first.authConfig()).isEqualTo(Map.of("apiKey", "******"));
        assertThat(first.healthStatus()).isEqualTo(ProviderHealthStatus.UP);
        assertThat(first.enabledModelCount()).isEqualTo(3);
        // 无健康记录、无启用模型的提供商：UNKNOWN + 0
        ProviderListItemDto second = result.records().get(1);
        assertThat(second.healthStatus()).isEqualTo(ProviderHealthStatus.UNKNOWN);
        assertThat(second.failCount()).isZero();
        assertThat(second.enabledModelCount()).isZero();
    }

    @Test
    void detailThrowsNotFoundWhenMissing() {
        when(providerRepository.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> providerService.getDetail(99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void detailAssemblesConfigsAndHealth() {
        when(providerRepository.findById(1L)).thenReturn(providerDto(1L));
        when(healthRepository.findByProviderId(1L))
                .thenReturn(new ProviderHealthDto(1L, 0, 42, null));

        ProviderDetailDto detail = providerService.getDetail(1L);

        assertThat(detail.provider().authConfig())
                .isEqualTo(Map.of("apiKey", "******"));
        assertThat(detail.health().latencyMs()).isEqualTo(42);
    }

    @Test
    void updatePreservesCodeAndMergesNullFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime oldUpdatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);
        ProviderDto existing = new ProviderDto(1L, "openai", ProviderType.OPENAI, "OpenAI",
                "https://api.openai.com", Map.of("apiKey", "sk-secret"), 1,
                createdAt, oldUpdatedAt);
        when(providerRepository.findById(1L)).thenReturn(existing);

        providerService.update(1L, new ProviderUpdateCmd(
                null, "New Name", null, null, null));

        ArgumentCaptor<ProviderDto> captor = ArgumentCaptor.forClass(ProviderDto.class);
        verify(providerRepository).update(captor.capture());
        ProviderDto merged = captor.getValue();
        assertThat(merged.code()).isEqualTo("openai");
        assertThat(merged.name()).isEqualTo("New Name");
        assertThat(merged.type()).isEqualTo(ProviderType.OPENAI);
        assertThat(merged.authConfig()).isEqualTo(Map.of("apiKey", "sk-secret"));
        // updatedAt 必须留空，由 MetaObjectHandler 填充；createdAt 原样保留
        assertThat(merged.updatedAt()).isNull();
        assertThat(merged.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void deleteCascadesConfigsAndHealth() {
        when(providerRepository.findById(1L)).thenReturn(providerDto(1L));

        providerService.delete(1L);

        verify(modelConfigRepository).deleteByProviderId(1L);
        verify(healthRepository).deleteByProviderId(1L);
        verify(providerRepository).deleteById(1L);
    }

    @Test
    void successfulTestResetsFailCountAndRecordsLatency() {
        when(providerRepository.findById(1L)).thenReturn(providerDto(1L));
        when(connectionTester.test(any()))
                .thenReturn(ConnectionTestResult.ok(35, 3));

        ConnectionTestResult result = providerService.testConnection(1L);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ProviderHealthDto> captor =
                ArgumentCaptor.forClass(ProviderHealthDto.class);
        verify(healthRepository).saveOrUpdate(captor.capture());
        ProviderHealthDto saved = captor.getValue();
        assertThat(saved.failCount()).isZero();
        assertThat(saved.latencyMs()).isEqualTo(35);
        assertThat(saved.lastSuccessAt()).isNotNull();
    }

    @Test
    void failedTestIncrementsFailCountAndKeepsLastSuccess() {
        when(providerRepository.findById(1L)).thenReturn(providerDto(1L));
        when(connectionTester.test(any()))
                .thenReturn(ConnectionTestResult.fail(10_000, "timeout"));
        when(healthRepository.findByProviderId(1L))
                .thenReturn(new ProviderHealthDto(1L, 2, 100, null));

        ConnectionTestResult result = providerService.testConnection(1L);

        assertThat(result.success()).isFalse();
        ArgumentCaptor<ProviderHealthDto> captor =
                ArgumentCaptor.forClass(ProviderHealthDto.class);
        verify(healthRepository).saveOrUpdate(captor.capture());
        ProviderHealthDto saved = captor.getValue();
        assertThat(saved.failCount()).isEqualTo(3);
        assertThat(saved.latencyMs()).isEqualTo(10_000);
    }
}
