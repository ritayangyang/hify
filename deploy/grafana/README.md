# Hify Grafana Dashboard

`hify-dashboard.json`：Grafana → Dashboards → New → Import → 上传该文件，导入时选择 Prometheus 数据源即可。面板默认时间窗 15 分钟、30s 刷新。

## 面板与查询

| # | 面板 | PromQL 要点 |
|---|---|---|
| 1 | 对话量 QPS（agent_id） | `sum by(agent_id)(rate(hify_chat_requests_total[$__rate_interval]))` |
| 2 | 对话延迟 P50/P95/P99（agent_id） | `histogram_quantile(0.95, sum by(le,agent_id)(rate(hify_chat_duration_ms_bucket[...])))` |
| 3 | LLM 成功率（provider） | success="true" 速率 / 全部速率 |
| 4 | LLM P95 延迟（provider） | 同 2 的 histogram_quantile，按 provider 聚合 |
| 5 | 熔断器状态（provider） | `max by(provider)(hify_circuit_breaker_state)`，0=CLOSED 绿 / 1=OPEN 红 / 2=HALF_OPEN 黄 |
| 6 | MCP 工具成功率（tool） | success="true" 速率 / 全部速率 |

查询未写死 `job` 标签，多个 hify-backend 副本会自动聚合；如 Prometheus 里还有别的服务暴露同名指标，再补 `{job="..."}` 过滤。

## 出数前提（当前代码尚未实现，导入后面板为空属正常）

1. 后端引入 `micrometer-registry-prometheus` 并开放 actuator 的 prometheus 端点（当前只暴露 health）：
   `management.endpoints.web.exposure.include: health,prometheus`。
2. 六个指标需在代码中埋点（建议位置）：
   - `hify_chat_requests_total` / `hify_chat_duration_ms`：`ChatServiceImpl` worker 终态处（Counter 按 agent_id，Timer/DistributionSummary 同标签）；
   - `hify_llm_calls_total` / `hify_llm_duration_ms`：`LlmChatServiceImpl`、`LlmStreamServiceImpl`，标签 provider/model/success；
   - `hify_circuit_breaker_state`：Resilience4j 启用后用 `EventConsumer` 或状态轮询写 Gauge（标签 provider）；
   - `hify_mcp_tool_calls_total`：mcp 模块落地后在工具调用出口埋点（标签 tool/success）。
3. **面板 2/4 的分位数依赖 histogram buckets**：DistributionSummary 需开启
   `.publishPercentileHistogram()`（或配置 `management.metrics.distribution.percentiles-histogram` 对应指标为 true），
   否则 Prometheus 里没有 `_bucket` 序列，P50/P95/P99 无数据。
4. success 标签按 Micrometer 惯例为字符串 `"true"/"false"`；若埋点时用 1/0，需同步改面板查询。
