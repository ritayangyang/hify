package com.hify.modules.model.web;

import com.hify.common.web.Result;
import com.hify.modules.model.api.dto.ModelOptionDto;
import com.hify.modules.model.api.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置只读接口。模型配置写入接口尚未实现，当前供 Agent 表单下拉使用。
 */
@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /** 已启用的对话模型选项（含提供商名称），用于 Agent 绑定模型下拉 */
    @GetMapping("/chat-options")
    public Result<List<ModelOptionDto>> chatOptions() {
        return Result.ok(modelConfigService.listEnabledChatOptions());
    }
}
