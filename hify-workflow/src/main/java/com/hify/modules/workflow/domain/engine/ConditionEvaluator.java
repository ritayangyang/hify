package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 条件分支表达式求值器（MVP 最小实现）。
 *
 * <p>表达式形如 {@code {{classify.intent}} == '售前'}，模板变量由
 * {@link ExecutionContext} 先完成替换，本类只负责结构为 {@code 左操作数 操作符 右操作数}
 * 的比较；操作符支持 {@code ==}、{@code !=}；操作数支持：
 * <ul>
 *   <li>单引号字符串：'售前'</li>
 *   <li>数字：1、2.5（按 BigDecimal 比较）</li>
 *   <li>布尔字面量：true / false</li>
 *   <li>裸字符串：替换后无引号的中文/单词按字符串处理，如 {@code 售后 == '售前'}</li>
 * </ul>
 */
@Component
public class ConditionEvaluator {

    /**
     * @param nodeId             条件节点 id（错误定位）
     * @param branchId           分支 id（错误定位）
     * @param resolvedExpression 已完成模板替换的表达式
     */
    public boolean evaluate(String nodeId, String branchId, String resolvedExpression) {
        if (resolvedExpression == null || resolvedExpression.isBlank()) {
            throw error(nodeId, branchId, resolvedExpression, "表达式为空");
        }
        int operatorIndex = findOperator(resolvedExpression);
        if (operatorIndex < 0) {
            throw error(nodeId, branchId, resolvedExpression, "缺少 == 或 != 操作符");
        }
        boolean equals = resolvedExpression.charAt(operatorIndex) == '=';
        String leftText = resolvedExpression.substring(0, operatorIndex).trim();
        String rightText = resolvedExpression.substring(operatorIndex + 2).trim();
        if (leftText.isEmpty() || rightText.isEmpty()) {
            throw error(nodeId, branchId, resolvedExpression, "操作数不完整");
        }

        Operand left = parseOperand(nodeId, branchId, resolvedExpression, leftText);
        Operand right = parseOperand(nodeId, branchId, resolvedExpression, rightText);
        boolean equal = left.equals(right);
        return equals == equal;
    }

    /** 找不在单引号内的 == / != 操作符位置 */
    private int findOperator(String expression) {
        boolean inQuote = false;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote && i + 1 < expression.length()
                    && (ch == '=' || ch == '!') && expression.charAt(i + 1) == '=') {
                return i;
            }
        }
        return -1;
    }

    private Operand parseOperand(String nodeId, String branchId, String raw, String text) {
        if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
            return new Operand(OperandKind.STRING, text.substring(1, text.length() - 1));
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return new Operand(OperandKind.BOOLEAN, Boolean.parseBoolean(text));
        }
        try {
            return new Operand(OperandKind.NUMBER, new BigDecimal(text));
        } catch (NumberFormatException ignored) {
            // 非数字也非引号字符串：替换后的裸值按字符串处理
            if (!text.matches("[\\w\\u4e00-\\u9fa5_.\\-]+")) {
                throw error(nodeId, branchId, raw, "无法解析的操作数: " + text);
            }
            return new Operand(OperandKind.STRING, text);
        }
    }

    private BizException error(String nodeId, String branchId, String expression, String reason) {
        return new BizException(ErrorCode.PARAM_ERROR,
                "条件节点 " + nodeId + " 分支 " + branchId + " 表达式非法（" + reason + "）: " + expression);
    }

    private enum OperandKind {
        STRING, NUMBER, BOOLEAN
    }

    private record Operand(OperandKind kind, Object value) {

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Operand(OperandKind otherKind, Object otherValue))) {
                return false;
            }
            if (kind != otherKind) {
                return false;
            }
            if (kind == OperandKind.NUMBER) {
                return ((BigDecimal) value).compareTo((BigDecimal) otherValue) == 0;
            }
            return value.equals(otherValue);
        }

        @Override
        public int hashCode() {
            // 与 equals 保持一致：数字按 compareTo 归一化，避免 1 与 1.0 哈希不同
            if (kind == OperandKind.NUMBER) {
                return Objects.hash(kind, ((BigDecimal) value).stripTrailingZeros());
            }
            return Objects.hash(kind, value);
        }
    }
}
