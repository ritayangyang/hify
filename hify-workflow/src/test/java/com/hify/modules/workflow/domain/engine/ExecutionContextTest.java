package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExecutionContext 变量池单测：预写入、节点隔离写入、严格模板渲染。
 */
class ExecutionContextTest {

    @Test
    void startInputsPreloadedUnderStartNamespace() {
        ExecutionContext context = new ExecutionContext(Map.of("userMessage", "我要退货"));

        assertThat(context.get("start", "userMessage")).isEqualTo("我要退货");
        assertThat(context.contains("start.userMessage")).isTrue();
        assertThat(context.getStartInputs()).containsEntry("userMessage", "我要退货");
    }

    @Test
    void resolveReplacesNodeScopedVariables() {
        ExecutionContext context = new ExecutionContext(Map.of("userMessage", "耳机坏了"));
        context.set("classify", "intent", "售后");

        String resolved = context.resolve("n2",
                "问题：{{ start.userMessage }}，类型：{{classify.intent}}客服为您服务");

        assertThat(resolved).isEqualTo("问题：耳机坏了，类型：售后客服为您服务");
    }

    @Test
    void resolveNullValueBecomesEmptyString() {
        ExecutionContext context = new ExecutionContext(Map.of());
        context.set("n1", "answer", null);

        assertThat(context.resolve("end", "结果：[{{n1.answer}}]")).isEqualTo("结果：[]");
    }

    @Test
    void resolveMissingVariableThrowsWithNames() {
        ExecutionContext context = new ExecutionContext(Map.of("userMessage", "你好"));

        assertThatThrownBy(() -> context.resolve("end", "{{presale.answer}}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("end")
                .hasMessageContaining("presale.answer");
    }

    @Test
    void snapshotIsImmutableAndOrdered() {
        ExecutionContext context = new ExecutionContext(Map.of("a", "1"));
        context.set("n1", "x", "2");

        Map<String, Object> snapshot = context.snapshot();

        assertThat(snapshot.keySet()).containsExactly("start.a", "n1.x");
        assertThatThrownBy(() -> snapshot.put("z", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullAndEmptyTemplateReturnedAsIs() {
        ExecutionContext context = new ExecutionContext(Map.of());

        assertThat(context.resolve("n1", "")).isEmpty();
        assertThat(context.resolve("n1", null)).isNull();
    }
}
