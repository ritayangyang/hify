package com.hify.modules.model.web;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResponse;
import com.hify.common.web.Result;
import com.hify.modules.model.api.dto.ConnectionTestCmd;
import com.hify.modules.model.api.dto.ConnectionTestResult;
import com.hify.modules.model.api.dto.ProviderCreateCmd;
import com.hify.modules.model.api.dto.ProviderDetailDto;
import com.hify.modules.model.api.dto.ProviderDto;
import com.hify.modules.model.api.dto.ProviderListItemDto;
import com.hify.modules.model.api.dto.ProviderPageQuery;
import com.hify.modules.model.api.dto.ProviderUpdateCmd;
import com.hify.modules.model.api.enums.ProviderType;
import com.hify.modules.model.api.service.ProviderService;
import com.hify.modules.model.web.req.ProviderConnectionTestRequest;
import com.hify.modules.model.web.req.ProviderCreateRequest;
import com.hify.modules.model.web.req.ProviderPageRequest;
import com.hify.modules.model.web.req.ProviderUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型提供商管理接口。
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    /** 创建提供商 */
    @PostMapping
    public Result<ProviderDto> create(@Valid @RequestBody ProviderCreateRequest request) {
        ProviderCreateCmd cmd = new ProviderCreateCmd(
                request.getCode(),
                request.getType(),
                request.getName(),
                request.getBaseUrl(),
                request.getAuthConfig(),
                request.getStatus());
        return Result.ok(providerService.create(cmd));
    }

    /** 分页查询提供商列表 */
    @GetMapping
    public Result<PageResponse<ProviderListItemDto>> page(@Valid ProviderPageRequest request) {
        ProviderPageQuery query = new ProviderPageQuery(
                request.getPage(),
                request.getSize(),
                request.getName(),
                parseType(request.getType()),
                request.getStatus());
        return Result.ok(providerService.page(query));
    }

    /** 查询参数里的类型筛选，非法值归 400 而非 500 */
    private ProviderType parseType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ProviderType.of(type);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, e.getMessage());
        }
    }

    /** 提供商详情：含模型配置与健康状态 */
    @GetMapping("/{id}")
    public Result<ProviderDetailDto> detail(@PathVariable Long id) {
        return Result.ok(providerService.getDetail(id));
    }

    /** 更新提供商 */
    @PutMapping("/{id}")
    public Result<ProviderDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ProviderUpdateRequest request) {
        ProviderUpdateCmd cmd = new ProviderUpdateCmd(
                request.getType(),
                request.getName(),
                request.getBaseUrl(),
                request.getAuthConfig(),
                request.getStatus());
        return Result.ok(providerService.update(id, cmd));
    }

    /** 删除提供商（逻辑删除，连带模型配置与健康状态） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    /** 对已保存的提供商做连通性测试，并回写健康状态 */
    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(providerService.testConnection(id));
    }

    /** 表单未保存时的连通性测试，直接传连接信息 */
    @PostMapping("/connection-test")
    public Result<ConnectionTestResult> testConnection(
            @Valid @RequestBody ProviderConnectionTestRequest request) {
        ConnectionTestCmd cmd = new ConnectionTestCmd(
                request.getType(),
                request.getBaseUrl(),
                request.getApiKey());
        return Result.ok(providerService.testConnection(cmd));
    }
}
