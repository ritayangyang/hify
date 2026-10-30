package com.hify.modules.workflow.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 执行状态：整次执行与节点执行共用同一组状态。
 * 数据库存字符串 code（VARCHAR）。
 */
@Getter
public enum RunStatus {

    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED");

    @EnumValue
    @JsonValue
    private final String code;

    RunStatus(String code) {
        this.code = code;
    }
}
