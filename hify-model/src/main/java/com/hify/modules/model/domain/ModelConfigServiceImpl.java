package com.hify.modules.model.domain;

import com.hify.modules.model.api.dto.ModelConfigDto;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.service.ModelConfigService;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.domain.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型配置只读服务：模型下拉选项与跨模块绑定信息装配。
 * 提供商名称在 model 模块内一次性批量装配，调用方无需再查提供商。
 */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final String UNKNOWN_PROVIDER = "未知提供商";

    private final ModelConfigRepository modelConfigRepository;
    private final ProviderRepository providerRepository;

    @Override
    public List<ModelOptionDto> listEnabledChatOptions() {
        return assemble(modelConfigRepository.listEnabledChat());
    }

    @Override
    public List<ModelOptionDto> listOptionsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return assemble(modelConfigRepository.listByIds(ids));
    }

    /**
     * 批量装配提供商名称：模型列表 1 条 SQL + 提供商 IN 查询 1 条 SQL，无 N+1。
     */
    private List<ModelOptionDto> assemble(List<ModelConfigDto> configs) {
        if (configs.isEmpty()) {
            return List.of();
        }
        Set<Long> providerIds = configs.stream()
                .map(ModelConfigDto::providerId)
                .collect(Collectors.toSet());
        Map<Long, String> providerNames = providerRepository.listByIds(providerIds).stream()
                .collect(Collectors.toMap(ProviderDto::id, ProviderDto::name, (a, b) -> a));

        return configs.stream()
                .map(config -> new ModelOptionDto(
                        config.id(),
                        config.name(),
                        config.modelId(),
                        config.modelType(),
                        config.status(),
                        config.providerId(),
                        providerNames.getOrDefault(config.providerId(), UNKNOWN_PROVIDER)))
                .toList();
    }
}
