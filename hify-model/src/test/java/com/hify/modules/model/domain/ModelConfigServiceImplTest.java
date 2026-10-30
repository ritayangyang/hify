package com.hify.modules.model.domain;

import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.domain.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelConfigServiceImplTest {

    @Mock
    private ModelConfigRepository modelConfigRepository;
    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ModelConfigServiceImpl modelConfigService;

    private ModelConfigDto config(Long id, Long providerId) {
        return new ModelConfigDto(id, providerId, "豆包 Seed 1.6", "doubao-seed-1-6-250615",
                1, 1, Map.of());
    }

    @Test
    void optionsAssembleProviderNamesInBatch() {
        when(modelConfigRepository.listEnabledChat())
                .thenReturn(List.of(config(7L, 5L), config(8L, 5L)));
        when(providerRepository.listByIds(anyCollection()))
                .thenReturn(List.of(new ProviderDto(5L, "ark", ProviderType.OPENAI_COMPATIBLE,
                        "火山方舟", "https://ark.cn-beijing.volces.com/api/v3", Map.of(), 1, null, null)));

        List<ModelOptionDto> options = modelConfigService.listEnabledChatOptions();

        assertThat(options).hasSize(2);
        assertThat(options.get(0).providerName()).isEqualTo("火山方舟");
        assertThat(options.get(0).modelId()).isEqualTo("doubao-seed-1-6-250615");
    }

    @Test
    void missingProviderFallsBackToUnknownName() {
        when(modelConfigRepository.listByIds(List.of(7L))).thenReturn(List.of(config(7L, 99L)));
        // 提供商已被逻辑删除，查询返回空
        when(providerRepository.listByIds(anyCollection())).thenReturn(List.of());

        ModelOptionDto option = modelConfigService.listOptionsByIds(List.of(7L)).get(0);

        assertThat(option.providerName()).isEqualTo("未知提供商");
    }

    @Test
    void emptyIdsReturnEmptyWithoutQuery() {
        assertThat(modelConfigService.listOptionsByIds(List.of())).isEmpty();
    }
}
