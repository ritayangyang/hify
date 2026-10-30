-- ============================================================
-- Hify · Agent 管理模块建表脚本
-- MySQL 8.x，InnoDB / utf8mb4 / utf8mb4_unicode_ci
-- 约定：t_ 前缀、snake_case、逻辑删除 deleted、时间 DATETIME(3)、不建物理外键
-- ============================================================

-- ------------------------------------------------------------
-- Agent 配置（名称、系统提示词、绑定模型、生成参数覆盖）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`            VARCHAR(128) NOT NULL COMMENT 'Agent 名称，活跃记录范围内应用层唯一',
    `description`     VARCHAR(512) NULL COMMENT 'Agent 简介',
    `system_prompt`   MEDIUMTEXT   NULL COMMENT '系统提示词',
    `model_config_id` BIGINT       NOT NULL COMMENT '绑定模型 t_model_config.id（不建物理外键）',
    `workflow_id`     BIGINT       NULL COMMENT '绑定工作流 t_workflow.id，NULL=普通对话（不建物理外键）',
    `model_params`    JSON         NULL COMMENT '生成参数覆盖：temperature/top_p/max_tokens',
    `status`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0 禁用，1 启用',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    -- 名称等值校验（existsByName）走该索引；列表状态筛选 + id 倒序分页走 idx_status
    KEY `idx_name` (`deleted`, `name`),
    KEY `idx_status` (`deleted`, `status`, `id`),
    -- 按绑定模型反查 Agent（模型停用/删除前校验引用）
    KEY `idx_model_config` (`model_config_id`, `deleted`),
    -- 按绑定工作流反查 Agent（工作流停用/删除前校验引用），含 deleted 满足索引规范
    KEY `idx_agent_workflow` (`workflow_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 配置';

-- ------------------------------------------------------------
-- Agent-知识库 关联（多对多，整体替换维护）
-- 资源表由 knowledge 模块后续创建，本期只存 id，不校验资源存在性
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_knowledge` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`     BIGINT      NOT NULL COMMENT 'Agent t_agent.id（不建物理外键）',
    `knowledge_id` BIGINT      NOT NULL COMMENT '知识库 id（knowledge 模块落地后生效）',
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_agent` (`agent_id`, `deleted`),
    KEY `idx_knowledge` (`knowledge_id`, `deleted`)
    -- 不建 UNIQUE(agent_id, knowledge_id, deleted)：deleted 仅 0/1，同一关联
    -- 第二次逻辑删除时 (a,k,1) 冲突，与 t_provider uk_code 同坑；去重由应用层保证
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent-知识库 关联';

-- ------------------------------------------------------------
-- Agent-MCP 工具 关联（多对多，整体替换维护）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_mcp_tool` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`     BIGINT      NOT NULL COMMENT 'Agent t_agent.id（不建物理外键）',
    `mcp_tool_id`  BIGINT      NOT NULL COMMENT 'MCP 工具 id（mcp 模块落地后生效）',
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_agent` (`agent_id`, `deleted`),
    KEY `idx_mcp_tool` (`mcp_tool_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent-MCP 工具 关联';

-- ------------------------------------------------------------
-- 增量变更（2026-10-14）：Agent 绑定工作流
-- NULL = 普通对话；非 NULL 时对话引擎改为触发工作流
-- 手工执行一次：
-- ALTER TABLE `t_agent`
--     ADD COLUMN `workflow_id` BIGINT NULL COMMENT '绑定工作流 t_workflow.id，NULL=普通对话（不建物理外键）' AFTER `model_config_id`,
--     ADD KEY `idx_agent_workflow` (`workflow_id`, `deleted`);
-- ------------------------------------------------------------
