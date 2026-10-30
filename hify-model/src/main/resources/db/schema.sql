-- ============================================================
-- Hify · 模型管理模块建表脚本
-- MySQL 8.x，InnoDB / utf8mb4 / utf8mb4_unicode_ci
-- 约定：t_ 前缀、snake_case、逻辑删除 deleted、时间 DATETIME(3)、不建物理外键
-- ============================================================

-- ------------------------------------------------------------
-- 模型提供商（OpenAI / Claude / Gemini / Ollama）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_provider` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32)  NOT NULL COMMENT '提供商编码：openai/claude/gemini/ollama，用于 fallback 路由',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '提供商类型（协议分发）：1 OPENAI，2 OPENAI_COMPATIBLE，3 ANTHROPIC，4 OLLAMA',
    `name`        VARCHAR(64)  NOT NULL COMMENT '提供商展示名称',
    `base_url`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'API 基础地址',
    `auth_config` JSON         NOT NULL COMMENT '认证配置，如 {"apiKey":"sk-xxx"}',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0 禁用，1 启用',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    -- 不加 UNIQUE(code, deleted)：TINYINT 软删标志只能取 0/1，同一 code 第二次删除时
    -- 两条记录会变成 (code,1) 触发唯一键冲突。活跃 code 的唯一性由应用层
    -- ProviderServiceImpl#existsByCode 保证，库内仅保留普通索引加速查询。
    KEY `idx_code` (`code`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型提供商';

-- ------------------------------------------------------------
-- 模型配置（一个提供商下多个模型；展示名与真实模型 ID 分开存）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_model_config` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_id` BIGINT       NOT NULL COMMENT '所属提供商 t_provider.id（不建物理外键）',
    `name`        VARCHAR(128) NOT NULL COMMENT '模型展示名称，如：GPT-4o 生产',
    `model_id`    VARCHAR(128) NOT NULL COMMENT '真实模型标识，如：gpt-4o / claude-3-5-sonnet',
    `model_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '模型类型：1 chat 对话，2 embedding',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0 禁用，1 启用',
    `extra_params` JSON        NULL COMMENT '额外参数，如 temperature/top_p/max_tokens 等',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_provider_id` (`provider_id`, `deleted`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型配置';

-- ------------------------------------------------------------
-- 提供商健康状态（独立表，与 t_provider 一对一）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_provider_health` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_id`     BIGINT      NOT NULL COMMENT '提供商 t_provider.id（不建物理外键）',
    `fail_count`      INT         NOT NULL DEFAULT 0 COMMENT '连续失败次数，成功后清零',
    `latency_ms`      INT         NULL COMMENT '最近一次调用延迟（毫秒）',
    `last_success_at` DATETIME(3) NULL COMMENT '最近一次成功调用时间',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_id` (`provider_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '提供商健康状态';
