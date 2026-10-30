package com.hify.modules.agent.web;

import com.hify.common.web.PageResponse;
import com.hify.common.web.Result;
import com.hify.modules.agent.api.dto.AgentCreateCmd;
import com.hify.modules.agent.api.dto.AgentDetailDto;
import com.hify.modules.agent.api.dto.AgentDto;
import com.hify.modules.agent.api.dto.AgentListItemDto;
import com.hify.modules.agent.api.dto.AgentPageQuery;
import com.hify.modules.agent.api.dto.AgentUpdateCmd;
import com.hify.modules.agent.api.service.AgentService;
import com.hify.modules.agent.web.req.AgentCreateRequest;
import com.hify.modules.agent.web.req.AgentPageRequest;
import com.hify.modules.agent.web.req.AgentUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 管理接口。
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** 创建 Agent */
    @PostMapping
    public Result<AgentDto> create(@Valid @RequestBody AgentCreateRequest request) {
        return Result.ok(agentService.create(toCreateCmd(request)));
    }

    /** 分页查询 Agent 列表 */
    @GetMapping
    public Result<PageResponse<AgentListItemDto>> page(@Valid AgentPageRequest request) {
        AgentPageQuery query = new AgentPageQuery(
                request.getPage(),
                request.getSize(),
                request.getName(),
                request.getStatus());
        return Result.ok(agentService.page(query));
    }

    /** Agent 详情：含绑定模型信息与关联资源 id */
    @GetMapping("/{id}")
    public Result<AgentDetailDto> detail(@PathVariable Long id) {
        return Result.ok(agentService.getDetail(id));
    }

    /** 更新 Agent（整体替换可编辑字段与关联） */
    @PutMapping("/{id}")
    public Result<AgentDto> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentUpdateRequest request) {
        return Result.ok(agentService.update(id, toUpdateCmd(request)));
    }

    /** 删除 Agent（逻辑删除，关联同步逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    private AgentCreateCmd toCreateCmd(AgentCreateRequest request) {
        return new AgentCreateCmd(
                request.getName(),
                request.getDescription(),
                request.getSystemPrompt(),
                request.getModelConfigId(),
                request.getModelParams(),
                request.getStatus(),
                request.getKnowledgeIds(),
                request.getMcpToolIds(),
                request.getWorkflowId());
    }

    private AgentUpdateCmd toUpdateCmd(AgentUpdateRequest request) {
        return new AgentUpdateCmd(
                request.getName(),
                request.getDescription(),
                request.getSystemPrompt(),
                request.getModelConfigId(),
                request.getModelParams(),
                request.getStatus(),
                request.getKnowledgeIds(),
                request.getMcpToolIds(),
                request.getWorkflowId());
    }
}
