package com.hify.modules.model.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDateTime;

/**
 * 提供商健康状态（由连通性测试历史派生，非独立存储字段）。
 * <ul>
 *   <li>UP：最近一次测试成功（failCount=0 且曾成功过）</li>
 *   <li>DEGRADED：曾经成功过，但最近连续测试失败（failCount&gt;0 且 lastSuccessAt!=null）</li>
 *   <li>DOWN：从未成功过且存在失败记录（failCount&gt;0 且 lastSuccessAt=null）</li>
 *   <li>UNKNOWN：从未测试（无失败计数也无成功记录）</li>
 * </ul>
 */
public enum ProviderHealthStatus {

    UP,
    DOWN,
    DEGRADED,
    UNKNOWN;

    @JsonValue
    public String value() {
        return name();
    }

    /**
     * 按健康表字段派生状态。
     *
     * @param failCount     连续失败次数，null 视为 0（尚无健康记录）
     * @param lastSuccessAt 最近一次成功时间
     */
    public static ProviderHealthStatus derive(Integer failCount, LocalDateTime lastSuccessAt) {
        int fails = failCount == null ? 0 : failCount;
        if (fails > 0) {
            return lastSuccessAt != null ? DEGRADED : DOWN;
        }
        return lastSuccessAt != null ? UP : UNKNOWN;
    }
}
