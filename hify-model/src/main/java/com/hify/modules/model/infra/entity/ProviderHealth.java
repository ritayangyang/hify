package com.hify.modules.model.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提供商健康状态，对应表 t_provider_health，与 {@link Provider} 一对一。
 * 高频更新的独立状态表，不继承 BaseEntity。
 */
@Data
@TableName("t_provider_health")
public class ProviderHealth implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提供商 t_provider.id */
    private Long providerId;

    /** 连续失败次数，成功后清零 */
    private Integer failCount;

    /** 最近一次调用延迟（毫秒） */
    private Integer latencyMs;

    /** 最近一次成功调用时间 */
    private LocalDateTime lastSuccessAt;
}
