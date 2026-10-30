package com.hify.modules.workflow.domain.engine.executor;

import com.hify.modules.model.api.dto.LlmChatCmd;
import com.hify.modules.model.api.dto.LlmChatMessage;
import com.hify.modules.model.api.dto.LlmChatResult;
import com.hify.modules.model.api.service.LlmChatService;
import com.hify.modules.workflow.api.dto.WorkflowNodeDto;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.domain.engine.ExecutionContext;
import com.hify.modules.workflow.domain.engine.NodeExecutor;
import com.hify.modules.workflow.domain.engine.NodeExecutorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 节点：渲染 prompt 模板 → 非流式调用模型 → 结果以 outputKey 写入变量池。
 * 模型可用性/鉴权/超时/上游错误统一由 {@link LlmChatService} 抛 BizException。
 */
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final LlmChatService llmChatService;

    @Override
    public NodeType type() {
        return NodeType.LLM;
    }

    @Override
    public NodeExecutorResult execute(WorkflowNodeDto node, ExecutionContext context) {
        LlmNodeConfig config = (LlmNodeConfig) node.config();
        // 严格渲染：引用了未产出的变量直接判失败，不把占位符发给模型
        String prompt = context.resolve(node.id(), config.promptTemplate());

        LlmChatCmd cmd = new LlmChatCmd(
                config.modelConfigId(),
                java.util.List.of(LlmChatMessage.user(prompt)),
                config.temperature(),
                config.maxTokens());
        LlmChatResult chat = llmChatService.chat(cmd);

        Map<String, Object> outputs = Map.of(config.outputKey(), chat.content());
        Map<String, Object> recordedInputs = new LinkedHashMap<>();
        recordedInputs.put("modelConfigId", config.modelConfigId());
        recordedInputs.put("prompt", prompt);
        return NodeExecutorResult.llm(outputs, recordedInputs,
                chat.promptTokens(), chat.completionTokens());
    }
}
