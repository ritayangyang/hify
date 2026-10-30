-- ============================================================
-- Hify · 工作流模块建表脚本
-- MySQL 8.x，InnoDB / utf8mb4 / utf8mb4_unicode_ci
-- 约定：t_ 前缀、snake_case、逻辑删除 deleted、时间 DATETIME(3)、不建物理外键
-- 存储模型：工作流基本信息 / 节点 / 连线 三张表，整体读写、同事务维护
-- ============================================================

-- ------------------------------------------------------------
-- 工作流基本信息（一行一个工作流定义）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_workflow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(128) NOT NULL COMMENT '工作流名称，活跃记录范围内应用层唯一',
    `description` VARCHAR(512) NULL COMMENT '工作流简介',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0 禁用，1 启用',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`deleted`, `name`),
    KEY `idx_status` (`deleted`, `status`, `id`)
    -- 不建 UNIQUE(name, deleted)：deleted 仅 0/1，同名第二次逻辑删除时 (name,1) 冲突；重名应用层拦
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流定义';

-- ------------------------------------------------------------
-- 工作流节点（一个工作流多行，随工作流整体替换维护，不做 diff）
-- node_id/node_type 是图内业务标识；类型相关的异构配置统一收在 config_json
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_workflow_node` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id` BIGINT      NOT NULL COMMENT '所属工作流 t_workflow.id（不建物理外键）',
    `node_id`     VARCHAR(64) NOT NULL COMMENT '图内节点业务 id（start/n2…），工作流内应用层唯一',
    `node_type`   VARCHAR(16) NOT NULL COMMENT '节点类型：START/LLM/CONDITION/TOOL/KNOWLEDGE/END',
    `config_json` JSON        NOT NULL DEFAULT (JSON_OBJECT()) COMMENT '按 node_type 多态解析的节点配置',
    `pos_x`       DOUBLE      NULL COMMENT '画布坐标 X（前端使用，后端不解释）',
    `pos_y`       DOUBLE      NULL COMMENT '画布坐标 Y（前端使用，后端不解释）',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`, `deleted`, `id`)
    -- 不建 UNIQUE(workflow_id, node_id, deleted)：软删同坑，节点 id 唯一性由应用层保证
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流节点';

-- ------------------------------------------------------------
-- 工作流连线（source → target；条件节点用 source_handle 区分走哪个分支）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_workflow_edge` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id`     BIGINT      NOT NULL COMMENT '所属工作流 t_workflow.id（不建物理外键）',
    `edge_id`         VARCHAR(64) NULL COMMENT '图内连线业务 id（前端生成，可空）',
    `source_node_id`  VARCHAR(64) NOT NULL COMMENT '起点节点业务 id',
    `source_handle`   VARCHAR(64) NULL COMMENT '条件分支 id（b1/default）；普通节点唯一出线为 NULL',
    `target_node_id`  VARCHAR(64) NOT NULL COMMENT '终点节点业务 id',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`, `deleted`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流连线';

-- ------------------------------------------------------------
-- 工作流执行记录（一次试运行/触发一行）
-- status: RUNNING / SUCCEEDED / FAILED；执行过程跨多次 LLM 调用，
-- 不用一个大事务包住（会长时间占用连接），各状态更新独立提交
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_workflow_run` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id`  BIGINT      NOT NULL COMMENT '所属工作流 t_workflow.id（不建物理外键）',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态：RUNNING/SUCCEEDED/FAILED',
    `inputs`       JSON        NOT NULL DEFAULT (JSON_OBJECT()) COMMENT '触发入参，如 {"userMessage":"..."}',
    `output`       MEDIUMTEXT  NULL COMMENT 'END 节点渲染后的最终输出',
    `error_message` VARCHAR(500) NULL COMMENT '失败原因（SUCCESS 为 NULL）',
    `elapsed_ms`   INT         NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
    `total_tokens` INT         NULL COMMENT '全部 LLM 节点 token 合计',
    `finished_at`  DATETIME(3) NULL COMMENT '结束时间，RUNNING 时为 NULL',
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（即开始执行时间）',
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_workflow` (`workflow_id`, `deleted`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流执行记录';

-- ------------------------------------------------------------
-- 节点执行记录（一次执行多行，按 id 升序即实际执行顺序）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_workflow_node_run` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`            BIGINT      NOT NULL COMMENT '所属执行记录 t_workflow_run.id（不建物理外键）',
    `node_id`           VARCHAR(64) NOT NULL COMMENT '图内节点业务 id',
    `node_type`         VARCHAR(16) NOT NULL COMMENT '节点类型：START/LLM/CONDITION/END 等',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态：RUNNING/SUCCEEDED/FAILED',
    `inputs`            JSON        NULL COMMENT '节点实际输入（模板渲染后的 prompt、命中等）',
    `outputs`           JSON        NULL COMMENT '节点实际输出（写入变量池的键值）',
    `error_message`     VARCHAR(500) NULL COMMENT '失败原因',
    `elapsed_ms`        INT         NOT NULL DEFAULT 0 COMMENT '节点耗时毫秒',
    `prompt_tokens`     INT         NULL COMMENT 'LLM 节点输入 token',
    `completion_tokens` INT         NULL COMMENT 'LLM 节点输出 token',
    `finished_at`       DATETIME(3) NULL COMMENT '结束时间',
    `created_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`           TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_run` (`run_id`, `deleted`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流节点执行记录';
