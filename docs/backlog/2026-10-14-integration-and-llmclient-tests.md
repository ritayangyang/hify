# 任务卡：集成测试基建 + P0 链路 + LLM 客户端契约测试

- 日期：2026-10-14 创建，2026-10-15 按 CLAUDE.md「集成测试清单」重排
- 模块：hify-bootstrap（新增 src/test）/ hify-conversation / hify-workflow / hify-agent / hify-model
- 优先级：P0（风险地图红线：多表事务边界与 SSE 分帧解析零真实环境验证）
- 完整清单与验收口径以 **CLAUDE.md「集成测试清单（2026-10-15 规划）」** 为准，本卡只排执行顺序与勾选。

## 背景

- 当前 130 个测试**全部是 Mockito 单测**：三表事务、JSON 列、FieldStrategy 解绑、游标、SSE 全链路从未被真实环境验证；
- 火山方舟四个模型配置全 404，成功链路长期无真实流量，需要 MockWebServer 提供可重复的 LLM 响应；
- 本机 MySQL 已就绪（127.0.0.1:3306，hify/hify123，见记忆 local-mysql）。

## 任务 0：基建（P0 用例开工前，约半天）

- [ ] hify-bootstrap 建 `src/test`，引入 `okhttp3:mockwebserver`（test scope，与 OkHttp 同版本）；
- [ ] `application-test.yml` 指向独立 schema **hify_test**，汇总各模块 db/schema.sql 初始化；
- [ ] `@Tag("integration")` + surefire 默认排除（`-Dgroups=integration` 才跑），普通 `mvn test` 不依赖 MySQL；
- [ ] `@TestConfiguration` 把 llmExecutor/llmStreamExecutor 换成同线程执行器；
- [ ] 种子：baseUrl 指向 MockWebServer 的 provider + chat model_config，key 用 `sk-test-xxx`。

## 任务 1：P0 集成链路（CLAUDE.md 清单 P0-1 ~ P0-7）

按清单建议顺序：

- [ ] **P0-6** Provider 凭据双契约 + 掩码回填 + 级联删（链路最短，用来磨基建）；
- [ ] **P0-1** 普通对话 SSE 成功全链路（多 chunk/usage/落库/title/回放鉴权头）；
- [ ] **P0-2** SSE 失败终态 404/500（error 帧、emitter 收尾、无孤儿 assistant、user 保留）；
- [ ] **P0-3** 工作流触发对话（run/node_run 落库、非流式客户端真实解析）；
- [ ] **P0-4** 工作流三表事务（脏图 POST/PUT 回滚、合法 PUT 旧删新插同事务）；
- [ ] **P0-5** Agent 绑定校验 + `workflowId=null` 真实解绑（JDBC 断言 IS NULL）；
- [ ] **P0-7** 删会话同事务级联 + 故障注入回滚 + 404/停用语义；
- [ ] 顺手做 **P1-2** 上下文窗口回归（同会话 3 条消息回放 LLM 请求，20 条窗口 + 升序）。
      注意：教程 IT-P0-03 设计为先红后绿（ASC 取最旧 bug），**Hify 初版已写对（DESC 取近 + 内存反转），本用例首次即绿**，是回归测试。

P1 其余（试运行三接口、游标、连通测试健康行、逻辑删除边界、options、@Valid 信封、CAS）与 P2 见 CLAUDE.md 清单，P0 完成后再排期。

## 任务 2：LLM 客户端 HTTP 契约测试（MockWebServer，可与任务 1 并行）

- [ ] 引入 `com.squareup.okhttp3:mockwebserver`（test scope，与 OkHttp 同版本 BOM）；
- [ ] `LlmStreamClientTest`：
      ① 正常流——多 chunk 推送（半包/粘包）、`choices[0].delta.content` 累积、末块 `usage` + `finish_reason`、`data: [DONE]` 收尾；
      ② 非 2xx——错误体原文透传为 BizException（4xx 不重试语义留档）；
      ③ onDelta 回调抛异常时 eventSource/call 均被 cancel（上游不继续计费）；
      ④ 连接建立后长时间无数据的行为（当前无 readTimeout，确认依赖上层 90s/200s 兜底即可，写成文档化断言）；
- [ ] `LlmChatClientTest`：非流式正常 usage 解析、401/404/500 错误转换、超时路径；
- [ ] 断言日志与异常 message 中**不含 apiKey**（用 Logback ListAppender 抓日志）。

## 任务 3（顺手，小，并入 P0-5/P1-3）

- [x] `workflowId=null` 解绑的仓储层回归 → 已升级为 P0-5 真实 MySQL 用例；
- [ ] 游标仓储集成测试：插入 25 行后首页/次页/末页无重复无遗漏，size 越界截断（P1-3）。

## 验收

- [ ] `mvn test`（不含 integration）保持全绿，不依赖本地 MySQL 与外网；
- [ ] `mvn test -Dgroups=integration` 在本机 MySQL（hify_test schema）全绿，不依赖外网与真实 Ark；
- [ ] CI 策略二选一在任务 0 定：备本机 MySQL 服务，或迁 Testcontainers（README/CLAUDE.md 补前置说明）；
- [ ] 测试失败输出能直接定位到具体表/具体帧，不用再人工 curl 复现。

## 备注

- Resilience4j 熔断重试仍按计划延后，本任务不引入；
- 工作流 P1-1 前需先完成 [工作流 3 END 收敛修复](2026-10-14-workflow3-converge-answer.md)；
- 任务 1/2 可并行，互不依赖；MockWebServer 依赖在任务 0 一次引入。
