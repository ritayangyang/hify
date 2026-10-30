package com.hify.modules.workflow.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hify.common.exception.BizException;
import com.hify.modules.workflow.api.enums.NodeType;
import com.hify.modules.workflow.api.model.ConditionNodeConfig;
import com.hify.modules.workflow.api.model.EndNodeConfig;
import com.hify.modules.workflow.api.model.LlmNodeConfig;
import com.hify.modules.workflow.api.model.NodeConfig;
import com.hify.modules.workflow.api.model.StartNodeConfig;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NodeConfigParser 单测：真实 ObjectMapper（与 Spring Boot 默认一致，忽略未知字段）
 * + 真实 Hibernate Validator，不 mock。
 */
class NodeConfigParserTest {

    private ObjectMapper objectMapper;
    private NodeConfigParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        parser = new NodeConfigParser(objectMapper, validator);
    }

    @Test
    void parseLlmConfigToTypedRecord() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"modelConfigId": 6, "promptTemplate": "识别意图", "temperature": 0.2,
                 "maxTokens": 1024, "outputKey": "intent", "futureField": "x"}
                """);

        NodeConfig config = parser.parseInput("n2", NodeType.LLM, raw);

        assertThat(config).isInstanceOf(LlmNodeConfig.class);
        LlmNodeConfig llm = (LlmNodeConfig) config;
        assertThat(llm.modelConfigId()).isEqualTo(6L);
        assertThat(llm.promptTemplate()).isEqualTo("识别意图");
        assertThat(llm.temperature()).isEqualByComparingTo(new BigDecimal("0.2"));
        assertThat(llm.maxTokens()).isEqualTo(1024);
        assertThat(llm.outputKey()).isEqualTo("intent");
        assertThat(config.type()).isEqualTo(NodeType.LLM);
    }

    @Test
    void parseMissingConfigAsEmptyStart() {
        NodeConfig config = parser.parseInput("start", NodeType.START, null);

        assertThat(config).isInstanceOf(StartNodeConfig.class);
        assertThat(((StartNodeConfig) config).inputs()).isEmpty();
    }

    @Test
    void parseStartConfigWithInputs() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"inputs": ["orderId", "userMessage"]}
                """);

        StartNodeConfig config = (StartNodeConfig) parser.parseInput("start", NodeType.START, raw);

        assertThat(config.inputs()).containsExactly("orderId", "userMessage");
    }

    @Test
    void parseConditionBranches() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"branches": [{"id": "b1", "expression": "{{n2.intent}} == '退货'"}]}
                """);

        NodeConfig config = parser.parseInput("c1", NodeType.CONDITION, raw);

        assertThat(config).isInstanceOf(ConditionNodeConfig.class);
        ConditionNodeConfig condition = (ConditionNodeConfig) config;
        assertThat(condition.branches()).hasSize(1);
        assertThat(condition.branches().get(0).id()).isEqualTo("b1");
    }

    @Test
    void parseEndConfig() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"replyTemplate": "{{n2.answer}}"}
                """);

        NodeConfig config = parser.parseInput("end", NodeType.END, raw);

        assertThat(config).isInstanceOf(EndNodeConfig.class);
        assertThat(((EndNodeConfig) config).replyTemplate()).isEqualTo("{{n2.answer}}");
    }

    @Test
    void rejectMissingRequiredField() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"modelConfigId": 6, "outputKey": "intent"}
                """);

        assertThatThrownBy(() -> parser.parseInput("n2", NodeType.LLM, raw))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("n2")
                .hasMessageContaining("promptTemplate");
    }

    @Test
    void rejectOutOfRangeTemperature() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"modelConfigId": 6, "promptTemplate": "hi", "temperature": 5, "outputKey": "o"}
                """);

        assertThatThrownBy(() -> parser.parseInput("n2", NodeType.LLM, raw))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    void rejectNonObjectConfig() throws Exception {
        JsonNode raw = objectMapper.readTree("[1, 2]");

        assertThatThrownBy(() -> parser.parseInput("n2", NodeType.LLM, raw))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("n2")
                .hasMessageContaining("config 必须是 JSON 对象");
    }

    @Test
    void rejectWrongFieldType() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"modelConfigId": "not-a-number", "promptTemplate": "hi", "outputKey": "o"}
                """);

        assertThatThrownBy(() -> parser.parseInput("n2", NodeType.LLM, raw))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("n2")
                .hasMessageContaining("配置解析失败");
    }

    @Test
    void toTreeStripsTypeAndRoundTrips() throws Exception {
        JsonNode raw = objectMapper.readTree("""
                {"modelConfigId": 6, "promptTemplate": "hi", "outputKey": "o"}
                """);
        NodeConfig config = parser.parseInput("n2", NodeType.LLM, raw);

        JsonNode tree = parser.toTree(config);

        assertThat(tree.has("type")).isFalse();
        assertThat(tree.get("modelConfigId").asLong()).isEqualTo(6L);

        // 落库形态（无 type）回读时与节点 type 列合并后必须能还原
        NodeConfig reparsed = parser.fromStorage("n2", NodeType.LLM, tree);
        assertThat(reparsed).isInstanceOf(LlmNodeConfig.class);
        assertThat(((LlmNodeConfig) reparsed).outputKey()).isEqualTo("o");
    }

    @Test
    void unknownTypeFromNameRejected() {
        assertThatThrownBy(() -> NodeType.fromName("XXX"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知节点类型: XXX");
    }
}
