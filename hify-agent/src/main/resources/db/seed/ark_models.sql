-- ============================================================
-- 种子数据：火山方舟（Ark）常用对话模型
-- Agent 模块的前置数据：模型配置 CRUD 尚未实现，先将 4 个真实可用的
-- Ark chat 模型写入 t_model_config，供 Agent 绑定模型下拉使用。
-- 幂等：按 (provider_id, model_id) 去重，可重复执行。
-- 执行：mysql -uhify -phify123 hify < ark_models.sql
-- ============================================================

INSERT INTO `t_model_config` (`provider_id`, `name`, `model_id`, `model_type`, `status`, `extra_params`)
SELECT p.`id`, v.`name`, v.`model_id`, 1, 1, NULL
FROM `t_provider` p
JOIN (
    SELECT '豆包 Seed 1.6'          AS name, 'doubao-seed-1-6-250615'        AS model_id
    UNION ALL SELECT '豆包 Seed 1.6 思考版', 'doubao-seed-1-6-thinking-250715'
    UNION ALL SELECT 'DeepSeek V3.1',        'deepseek-v3-1-250821'
    UNION ALL SELECT 'Kimi K2',               'kimi-k2-250905'
) v
WHERE p.`code` = 'ark' AND p.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `t_model_config` mc
      WHERE mc.`provider_id` = p.`id`
        AND mc.`model_id` = v.`model_id`
        AND mc.`deleted` = 0
  );
