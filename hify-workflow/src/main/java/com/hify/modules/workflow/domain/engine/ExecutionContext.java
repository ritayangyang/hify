package com.hify.modules.workflow.domain.engine;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次工作流执行的变量池（对标 Dify VariablePool）。
 *
 * <ul>
 *   <li>key 格式 {@code nodeId.outputKey}，如 {@code classify.intent}；</li>
 *   <li>START 入参在执行前以 {@code start.xxx} 预写入；</li>
 *   <li>节点只能写自己 nodeId 命名空间下的变量，可读任意已产出变量；</li>
 *   <li>{@link #resolve(String, String)} 严格解析：引用了未产出变量直接抛
 *       {@link BizException}，让错误配置在执行期立即暴露，不输出带占位符的回复。</li>
 * </ul>
 * 实例为单次执行私有（方法内对象），不做线程共享。
 */
public class ExecutionContext {

    /** 模板变量：{{ nodeId.key }}，节点 id/键允许字母数字下划线、连字符、点 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z_][\\w.-]*)\\s*}}");

    public static final String START_NODE_ID = "start";

    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, Object> startInputs = new LinkedHashMap<>();

    public ExecutionContext(Map<String, Object> initialInputs) {
        if (initialInputs != null) {
            initialInputs.forEach((key, value) -> {
                startInputs.put(key, value);
                set(START_NODE_ID, key, value);
            });
        }
    }

    /** START 节点的触发入参（不带 start. 前缀），只读 */
    public Map<String, Object> getStartInputs() {
        return Collections.unmodifiableMap(startInputs);
    }

    /** 写入节点输出变量；key 为 null/空白属于配置错误 */
    public void set(String nodeId, String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "节点 " + nodeId + " 的输出键名不能为空");
        }
        variables.put(nodeId + "." + key, value);
    }

    public Object get(String nodeId, String key) {
        return variables.get(nodeId + "." + key);
    }

    public boolean contains(String reference) {
        return variables.containsKey(reference);
    }

    /**
     * 渲染模板字符串。
     *
     * @param currentNodeId 当前节点 id（错误信息定位用）
     * @param template      含 {@code {{nodeId.key}}} 的模板
     * @return 渲染后的字符串；值为 null 时替换为空串
     * @throws BizException 存在未产出的变量引用（节点不在执行路径上或没有该输出）
     */
    public String resolve(String currentNodeId, String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        java.util.List<String> missing = new java.util.ArrayList<>();
        while (matcher.find()) {
            String reference = matcher.group(1);
            if (!variables.containsKey(reference)) {
                missing.add(reference);
                continue;
            }
            Object value = variables.get(reference);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(builder);
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "节点 " + currentNodeId + " 模板引用了未产出的变量: "
                            + String.join(", ", missing)
                            + "（上游节点未执行或没有该输出）");
        }
        return builder.toString();
    }

    /** 当前已产出变量的只读视图（调试/日志用） */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
