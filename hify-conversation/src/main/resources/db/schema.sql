-- ============================================================
-- Hify · 对话引擎建表脚本
-- MySQL 8.x，InnoDB / utf8mb4 / utf8mb4_unicode_ci
-- 约定：t_ 前缀、snake_case、逻辑删除 deleted、时间 DATETIME(3)、不建物理外键
-- ============================================================

-- ------------------------------------------------------------
-- 对话会话：一次 Agent 对话的容器，先建会话再发消息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_chat_session` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`   BIGINT       NOT NULL COMMENT '所属 Agent t_agent.id（不建物理外键）',
    `title`      VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题，默认新会话，首条消息后取内容前 20 字',
    `status`     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    -- 按 Agent 反查会话 + 游标分页（agent_id 等值、deleted 纳入、id 游标/排序）
    KEY `idx_agent` (`agent_id`, `deleted`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话会话';

-- ------------------------------------------------------------
-- 对话消息：user / assistant 文本消息（system 只注入不落库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_chat_message` (
    `id`            BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`    BIGINT     NOT NULL COMMENT '所属会话 t_chat_session.id（不建物理外键）',
    `role`          VARCHAR(16) NOT NULL COMMENT '角色：user / assistant',
    `content`       MEDIUMTEXT NOT NULL COMMENT '消息文本',
    `tokens`        INT        NULL COMMENT '本条消耗 token 总数（prompt+completion），未知为 NULL',
    `finish_reason` VARCHAR(16) NULL COMMENT '结束原因：stop / length',
    `latency_ms`    INT        NULL COMMENT '耗时毫秒',
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    -- 上下文装载（session 等值 + id DESC）与消息游标分页共用，禁止 offset 深分页
    KEY `idx_session` (`session_id`, `deleted`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息';
