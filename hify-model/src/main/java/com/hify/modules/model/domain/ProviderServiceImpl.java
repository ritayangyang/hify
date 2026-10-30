package com.hify.modules.model.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.modules.model.api.dto.ConnectionTestCmd;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.dto.ProviderCreateCmd;
import com.hify.modules.model.api.dto.ProviderDetailDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.dto.ProviderHealthDto;
import com.hify.modules.model.api.dto.ProviderListItemDto;
import com.hify.modules.model.api.dto.ProviderPageQuery;
import com.hify.modules.model.api.dto.ProviderUpdateCmd;
import com.hify.modules.model.api.enums.ProviderHealthStatus;
import com.hify.modules.model.api.service.ProviderService;
import com.hify.modules.model.domain.repository.ModelConfigRepository;
import com.hify.modules.model.domain.repository.ProviderHealthRepository;
import com.hify.modules.model.domain.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型提供商服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private static final String API_KEY_FIELD = "apiKey";
    private static final String MASKED_KEY = "******";

    private final ProviderRepository providerRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ProviderHealthRepository healthRepository;
    private final ProviderConnectionTester connectionTester;

    @Override
    @Transactional
    public ProviderDto create(ProviderCreateCmd cmd) {
        // code 留空时按类型自动生成（openai / anthropic / ollama ...），同类型建多个时需显式传 code
        String code = StringUtils.hasText(cmd.code())
                ? cmd.code() : cmd.type().name().toLowerCase(Locale.ROOT);
        if (providerRepository.existsByCode(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "提供商编码已存在: " + code + "，同类型创建多个提供商时请显式指定 code");
        }
        // OLLAMA 等无认证类型允许不传 authConfig，库表 auth_config 为 NOT NULL，归一化为空 JSON 对象
        Map<String, Object> authConfig = cmd.authConfig() == null
                ? Map.of() : cmd.authConfig();
        ProviderDto created = providerRepository.insert(new ProviderDto(
                null,
                code,
                cmd.type(),
                cmd.name(),
                cmd.baseUrl(),
                authConfig,
                cmd.status() == null ? 1 : cmd.status(),
                null,
                null));

        healthRepository.saveOrUpdate(
                new ProviderHealthDto(created.id(), 0, null, null));
        return mask(created);
    }

    @Override
    public PageResponse<ProviderListItemDto> page(ProviderPageQuery query) {
        PageResponse<ProviderDto> result = providerRepository.page(
                query.page(), query.size(), query.name(), query.type(), query.status());
        List<ProviderDto> providers = result.records();
        if (providers.isEmpty()) {
            return PageResponse.of(List.of(), result.total(), result.page(), result.size());
        }

        // 本页 id 集合批量装配健康状态与模型数：固定 3 条 SQL，禁止逐行查询（N+1）
        List<Long> ids = providers.stream().map(ProviderDto::id).toList();
        Map<Long, ProviderHealthDto> healthMap = healthRepository.listByProviderIds(ids).stream()
                .collect(Collectors.toMap(ProviderHealthDto::providerId, Function.identity()));
        Map<Long, Integer> modelCountMap = modelConfigRepository.countEnabledByProviderIds(ids);

        List<ProviderListItemDto> records = providers.stream()
                .map(this::mask)
                .map(provider -> toListItem(provider,
                        healthMap.get(provider.id()),
                        modelCountMap.getOrDefault(provider.id(), 0)))
                .toList();
        return PageResponse.of(records, result.total(), result.page(), result.size());
    }

    private ProviderListItemDto toListItem(ProviderDto provider, ProviderHealthDto health, int enabledModelCount) {
        Integer failCount = health == null || health.failCount() == null ? 0 : health.failCount();
        Integer latencyMs = health == null ? null : health.latencyMs();
        LocalDateTime lastSuccessAt = health == null ? null : health.lastSuccessAt();
        return new ProviderListItemDto(
                provider.id(),
                provider.code(),
                provider.type(),
                provider.name(),
                provider.baseUrl(),
                provider.authConfig(),
                provider.status(),
                provider.createdAt(),
                provider.updatedAt(),
                failCount,
                latencyMs,
                lastSuccessAt,
                ProviderHealthStatus.derive(failCount, lastSuccessAt),
                enabledModelCount);
    }

    @Override
    public ProviderDetailDto getDetail(Long id) {
        ProviderDto provider = providerRepository.findById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在: " + id);
        }
        ProviderHealthDto health = healthRepository.findByProviderId(id);
        if (health == null) {
            health = new ProviderHealthDto(id, 0, null, null);
        }
        return new ProviderDetailDto(
                mask(provider),
                modelConfigRepository.listByProviderId(id),
                health);
    }

    @Override
    @Transactional
    public ProviderDto update(Long id, ProviderUpdateCmd cmd) {
        ProviderDto existing = providerRepository.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在: " + id);
        }
        ProviderDto merged = new ProviderDto(
                id,
                existing.code(),
                cmd.type() == null ? existing.type() : cmd.type(),
                StringUtils.hasText(cmd.name()) ? cmd.name() : existing.name(),
                StringUtils.hasText(cmd.baseUrl()) ? cmd.baseUrl() : existing.baseUrl(),
                cmd.authConfig() == null ? existing.authConfig() : cmd.authConfig(),
                cmd.status() == null ? existing.status() : cmd.status(),
                existing.createdAt(),
                // updatedAt 置空，交给 MetaObjectHandler 在 UPDATE 时自动填充为当前时间（非空值会被跳过填充）
                null);
        providerRepository.update(merged);
        return mask(providerRepository.findById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (providerRepository.findById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在: " + id);
        }
        modelConfigRepository.deleteByProviderId(id);
        healthRepository.deleteByProviderId(id);
        providerRepository.deleteById(id);
    }

    @Override
    public ConnectionTestResult testConnection(Long id) {
        ProviderDto provider = providerRepository.findById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在: " + id);
        }
        String apiKey = extractApiKey(provider.authConfig());
        ConnectionTestResult result = connectionTester.test(
                new ConnectionTestCmd(provider.type(), provider.baseUrl(), apiKey));
        recordHealth(id, result);
        return result;
    }

    @Override
    public ConnectionTestResult testConnection(ConnectionTestCmd cmd) {
        return connectionTester.test(cmd);
    }

    private void recordHealth(Long providerId, ConnectionTestResult result) {
        ProviderHealthDto previous = healthRepository.findByProviderId(providerId);
        int previousFailCount = previous == null ? 0 : previous.failCount();
        LocalDateTime lastSuccessAt = previous == null ? null : previous.lastSuccessAt();

        if (result.success()) {
            healthRepository.saveOrUpdate(new ProviderHealthDto(
                    providerId,
                    0,
                    (int) result.latencyMs(),
                    LocalDateTime.now()));
        } else {
            healthRepository.saveOrUpdate(new ProviderHealthDto(
                    providerId,
                    previousFailCount + 1,
                    (int) result.latencyMs(),
                    lastSuccessAt));
        }
    }

    private String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null) {
            return null;
        }
        Object apiKey = authConfig.get(API_KEY_FIELD);
        return apiKey == null ? null : String.valueOf(apiKey);
    }

    /** 对外出参脱敏：不回传真实 apiKey，仅表明是否已配置 */
    private ProviderDto mask(ProviderDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Object> maskedAuth = null;
        if (dto.authConfig() != null) {
            boolean configured = dto.authConfig().get(API_KEY_FIELD) != null;
            maskedAuth = configured
                    ? Map.of(API_KEY_FIELD, MASKED_KEY)
                    : Map.of();
        }
        return new ProviderDto(
                dto.id(), dto.code(), dto.type(), dto.name(), dto.baseUrl(),
                maskedAuth, dto.status(), dto.createdAt(), dto.updatedAt());
    }
}
