package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ConditionEvaluator 单测：== / !=、字符串/数字/布尔/裸字符串、非法表达式。
 * 入参均为模板替换后的表达式。
 */
class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void barewordEqualsQuotedString() {
        // {{classify.intent}} 替换为裸中文后的形态
        assertThat(evaluator.evaluate("router", "b1", "售后 == '售后'")).isTrue();
        assertThat(evaluator.evaluate("router", "b1", "售前 == '售后'")).isFalse();
    }

    @Test
    void notEquals() {
        assertThat(evaluator.evaluate("router", "b1", "技术支持 != '售后'")).isTrue();
        assertThat(evaluator.evaluate("router", "b1", "'售后' != '售后'")).isFalse();
    }

    @Test
    void numbersComparedByValue() {
        assertThat(evaluator.evaluate("router", "b1", "1 == 1.0")).isTrue();
        assertThat(evaluator.evaluate("router", "b1", "2 != 2")).isFalse();
    }

    @Test
    void booleans() {
        assertThat(evaluator.evaluate("router", "b1", "true == true")).isTrue();
        assertThat(evaluator.evaluate("router", "b1", "false == true")).isFalse();
    }

    @Test
    void operatorInsideQuotesIsIgnored() {
        assertThat(evaluator.evaluate("router", "b1", "'a==b' == 'a==b'")).isTrue();
    }

    @Test
    void missingOperatorThrows() {
        assertThatThrownBy(() -> evaluator.evaluate("router", "b1", "售后"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("router")
                .hasMessageContaining("b1");
    }

    @Test
    void emptyExpressionThrows() {
        assertThatThrownBy(() -> evaluator.evaluate("router", "b1", "  "))
                .isInstanceOf(BizException.class);
    }
}
