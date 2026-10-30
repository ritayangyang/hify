# Hify 项目开发规范

## 项目概览

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计。

- **团队规模**：1 人开发，20-50 人内部使用，本地部署
- **技术栈**：Spring Boot + MyBatis-Plus + Vue + MySQL 8.x + Redis + pgvector
- **架构模式**：模块化单体（Modular Monolith），代码边界清晰，可平滑拆分为微服务

---

## 核心功能模块（MVP 范围）

| 模块 | 说明 |
|------|------|
| 模型管理 (model) | 管理 OpenAI / Claude / Gemini / Ollama 等 LLM 提供商配置，支持连通性测试 |
| Agent 配置 (agent) | 配置 Agent 名称、系统提示词、绑定模型、关联知识库和 MCP 工具 |
| 对话引擎 (conversation) | 多轮对话、历史记录、SSE 流式响应 |
| 知识库 RAG (knowledge) | 文档上传 → 异步向量化 → pgvector 余弦搜索 → 注入 LLM 上下文 |
| 简版工作流 (workflow) | 顺序节点执行：开始 → LLM → 条件分支 → 工具调用 → 结束 |
| MCP 工具接入 (mcp) | 接入外部 MCP 工具，供 Agent 和工作流调用 |

**砍掉的功能**：多租户、自定义插件市场、实时协作、企业 SSO、精细化权限控制、数据集版本管理。

---

## 代码组织规范

### 包结构

com.hify
├── common/
│   ├── config/          # MybatisPlusConfig, JacksonConfig, AsyncConfig, ThreadPoolConfig
│   ├── exception/       # BizException, ErrorCode, GlobalExceptionHandler
│   ├── web/             # Result<T>, PageResult<T>
│   └── util/            # JsonUtil, DateUtil
├── modules/
│   ├── model/           # LLM 提供商管理
│   ├── agent/           # Agent 配置
│   ├── conversation/    # 对话引擎
│   ├── knowledge/       # 知识库 RAG
│   ├── workflow/        # 简版工作流
│   └── mcp/             # MCP 工具接入
└── HifyApplication.java

每个模块内部四层结构：

modules/{module}/
├── api/       # 对外暴露的接口（interface），供其他模块调用
├── domain/    # 业务逻辑：Service 实现、领域对象、Factory、Repository 接口
├── infra/     # 基础设施：Mapper、RepositoryImpl、外部 API 客户端、config
└── web/       # Controller，只处理 HTTP 层

### 各层职责边界

| 层 | 职责 | 禁止 |
|----|------|------|
| web/ | 接收请求、参数校验（@Valid）、调用本模块 api/ 接口、返回 Result<T> | 直接调用其他模块 domain/、直接操作数据库 |
| api/ | 定义跨模块调用的 interface 和 DTO | 包含业务逻辑实现 |
| domain/ | 业务逻辑、领域对象、事务边界（@Transactional） | 直接依赖 Mapper、依赖 web 层 |
| infra/ | Mapper、RepositoryImpl（PO ↔ 领域对象转换）、外部调用 | 包含业务逻辑 |

### 跨模块调用规则

- **只能**通过目标模块的 `api/` 接口调用，禁止直接 import 其他模块的 `domain/` 或 `infra/` 类
- 跨模块传递使用 `api/` 包下定义的 DTO，不传递 PO 或领域对象
- 循环依赖视为架构错误，立即重构

```java
// 正确：agent 模块通过 ModelService（api/ 接口）调用 model 模块
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private final ModelService modelService; // 来自 model 模块的 api/ 接口
}
```

---

## LLM 调用规范

### 线程池配置

```java
// llm-pool: 非流式调用（阻塞等待完整响应）
@Bean("llmExecutor")
public ThreadPoolExecutor llmExecutor() {
    return new ThreadPoolExecutor(20, 50, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadFactoryBuilder().setNameFormat("llm-pool-%d").setDaemon(true).build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 满载时调用方线程执行，不丢任务
    );
}

// llm-stream: 流式 SSE 调用（长连接）
@Bean("llmStreamExecutor")
public ThreadPoolExecutor llmStreamExecutor() {
    return new ThreadPoolExecutor(30, 80, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(50),
        new ThreadFactoryBuilder().setNameFormat("llm-stream-%d").setDaemon(true).build(),
        new AbortPolicy()  // 流式超限直接拒绝，由上层返回 503
    );
}
```

### OkHttpClient 配置

```java
// 非流式：有 readTimeout
@Bean("standardLlmClient")
public OkHttpClient standardLlmClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
        .addInterceptor(new LoggingInterceptor())
        .build();
}

// 流式：readTimeout 设为 0（SSE 不能有读超时）
@Bean("streamLlmClient")
public OkHttpClient streamLlmClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
}
```

### 超时层次（三层保护）

1. OkHttp connectTimeout = 5s（TCP 握手超时）
2. OkHttp readTimeout = 120s（单次读取超时，仅非流式）
3. CompletableFuture.get(90, TimeUnit.SECONDS)（总体超时兜底）

### 重试策略（Resilience4j）

- 普通 LLM：最多 3 次，初始等待 500ms，指数退避 2x，最大等待 10s
- Ollama（本地）：最多 5 次，初始等待 2s
- 仅对网络异常和 5xx 重试，4xx（参数错误）不重试

### 熔断器配置

```yaml
# COUNT_BASED 滑动窗口，20 次请求内失败率 >50% 触发熔断
# 慢调用（>30s）超过 80% 也触发熔断
# 熔断后等待 30s 进入 half-open，放行 5 次探测
failure-rate-threshold: 50
slow-call-duration-threshold: 30s
slow-call-rate-threshold: 80
wait-duration-in-open-state: 30s
permitted-calls-in-half-open-state: 5
```

### Fallback 路由

```yaml
hify.llm.fallback:
  openai: ollama
  claude: openai
  gemini: ollama
```

主 Provider 熔断或异常时自动切换 fallback，fallback 失败则抛出 BizException。

---

## 部署架构

用户浏览器
    │
    ▼
Ingress Nginx（L7 负载均衡 + SSL 终止 + SSE 支持）
    │
    ├──▶ hify-frontend（Vue SPA，Nginx 静态文件服务，2 副本）
    │
    └──▶ hify-backend（Spring Boot，2 副本）
              │
              ├──▶ MySQL 8.x（主数据存储）
              ├──▶ Redis（Session / 缓存 / 限流）
              └──▶ PostgreSQL + pgvector（向量存储）

**Ingress 关键配置（SSE 必须）**：

```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
nginx.ingress.kubernetes.io/proxy-buffering: "off"
nginx.ingress.kubernetes.io/limit-rps: "20"
```

**Backend 容器规格**：requests 512Mi/250m，limits 1Gi/1000m，replicas=2

**JVM 启动参数**：

```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC",
            "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

---

## 数据库规范

### MySQL 通用字段约定

每张表必须包含以下字段：

```sql
id          BIGINT          NOT NULL AUTO_INCREMENT,  -- 主键，禁用 UUID
created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
deleted     TINYINT(1)      NOT NULL DEFAULT 0,       -- 逻辑删除标志
PRIMARY KEY (id)
```

- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`
- 禁用 `VARCHAR` 无长度约束，text 类 content 字段用 `MEDIUMTEXT`
- 金额用 `DECIMAL(19,4)`，禁用 `FLOAT/DOUBLE`
- 布尔用 `TINYINT(1)`，不用 `BIT`

### 索引设计原则

1. **区分度低的字段不单独建索引**（如 deleted、status 枚举），必须与高区分度字段组合
2. **组合索引遵循最左前缀**：等值查询字段在左，范围查询字段在右
3. **查询条件中含 `deleted`**，必须将 `deleted` 纳入索引
4. **每表索引不超过 5 个**（含主键），写多读少的表控制在 3 个以内
5. **禁止在 `TEXT/BLOB` 类型字段上建普通索引**，需要时建前缀索引或全文索引

```sql
-- 正确示例：conversation_id 高区分度在左，deleted 次之，created_at 范围在右
INDEX idx_conv_created (conversation_id, deleted, created_at)
```

### 大表处理策略

判断为大表的阈值：行数 > 500 万 或 数据量 > 2GB

| 场景 | 策略 |
|------|------|
| t_message | 按 conversation_id 分区，或按月归档冷数据 |
| 知识库向量表 | ivfflat 索引，lists = sqrt(行数) |
| 日志类表 | 只保留 90 天，定期 DELETE + OPTIMIZE TABLE |

### 分页查询规范

- **禁止** `LIMIT offset, size` 深分页（offset > 1000 全表扫描）
- 对话记录类使用**游标分页**：

```sql
SELECT id, role, content, created_at FROM t_message
WHERE conversation_id = ?
  AND deleted = 0
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

- 管理后台必须分页时，用 `WHERE id > lastId LIMIT size` 替代 offset

### pgvector 索引规范

```sql
-- 余弦相似度索引，lists 值 = sqrt(总行数)，行数 <10 万时 lists=100
CREATE INDEX idx_embedding_ivfflat ON knowledge_embedding
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 查询时设置 probes，精度和速度平衡
SET ivfflat.probes = 10;
SELECT * FROM knowledge_embedding
ORDER BY embedding <=> '[...]'::vector LIMIT 5;
```

### 索引检测措施

**开发阶段**：启用 p6spy，拦截执行 >10ms 的查询自动 EXPLAIN，type=ALL 时打印警告日志。

**CI 阶段**：关键查询写 `IndexCoverageTest`，EXPLAIN 结果中 type=ALL 则测试失败，阻断合并。

**生产阶段**：定期查询 `performance_schema.events_statements_summary_by_digest`，找出 `sum_no_index_used > 0` 的 SQL。

```sql
SELECT digest_text, count_star AS 执行次数, sum_no_index_used AS 未用索引次数
FROM performance_schema.events_statements_summary_by_digest
WHERE sum_no_index_used > 0
ORDER BY sum_no_index_used DESC LIMIT 20;
```

---

## 编码规范（基于阿里巴巴 Java 开发手册）

### 命名

1. **类名用 UpperCamelCase**，方法名、变量名用 lowerCamelCase，常量用 UPPER_SNAKE_CASE，包名全小写无下划线。
2. **禁止用拼音或拼音缩写**命名，禁止单字母变量（循环变量 `i/j/k` 除外）。
3. **方法名体现动词**：查询用 `get/list/query`，修改用 `update`，删除用 `delete/remove`，新增用 `create/add`，布尔返回值用 `is/has/can`。
4. **Service 接口不加 I 前缀**，实现类加 `Impl` 后缀（`AgentService` + `AgentServiceImpl`）。
5. **数据库表名用 `t_` 前缀**，列名用 snake_case；PO 类用 `Po` 后缀，DTO 用 `Dto`/`Request`/`Response`，Mapper 用 `Mapper` 后缀。

### 异常处理

6. **禁止 catch 后 `e.printStackTrace()` 或空 catch**，必须记录日志或向上抛出。
7. **业务异常统一抛 `BizException(ErrorCode)`**，不用 RuntimeException 传递业务语义。
8. **只在顶层（GlobalExceptionHandler）处理并转换为 HTTP 响应**，中间层不捕获再包装。
9. **finally 块不写 return**，不在 finally 中抛出新异常（会吞掉原始异常）。
10. **NPE 防御**：方法返回值优先返回空集合（`Collections.emptyList()`）而非 null，接口入参用 `@NonNull`/`@Valid` 注解声明约束。

### 日志

11. **使用 SLF4J 接口 + Logback 实现**，类中用 `@Slf4j`（Lombok），禁止用 `System.out.println`。
12. **禁止在循环体内打日志**，高频路径只在异常分支记录。
13. **占位符格式 `log.info("xxx {}", var)`**，禁止字符串拼接（避免无效 toString 开销）。
14. **日志分级约定**：DEBUG=详细调试，INFO=关键业务节点，WARN=可恢复异常或配置缺失，ERROR=需人工介入的故障。生产环境 INFO 级别，日志文件按天滚动，保留 30 天。
15. **LLM 调用必须记录**：provider、model、耗时、token 数、是否命中缓存，便于成本分析。

### 并发

16. **线程池必须显式创建**（`ThreadPoolExecutor`），禁止用 `Executors.newFixedThreadPool`（无界队列 OOM）。
17. **ThreadLocal 用完必须 `remove()`**，防止线程池场景下数据泄漏。
18. **加锁粒度最小化**：只锁共享变量操作，不锁 I/O 和 LLM 调用；优先用 `ReentrantLock` 替代 `synchronized`（可设超时）。
19. **单例 Bean 的成员变量必须是线程安全的**：无状态 Service 天然安全；有状态则用 `ThreadLocal` 或局部变量，禁止用实例变量存请求上下文。
20. **`CompletableFuture` 异步调用必须指定线程池**（`supplyAsync(task, llmExecutor)`），禁止用默认 `ForkJoinPool.commonPool()`（会影响其他异步任务）。

---

## 性能瓶颈优先级（一期处理清单）

| 级别 | 瓶颈 | 一期处理方式 |
|------|------|-------------|
| P0 | LLM API 延迟高（3-30s） | 线程隔离 + 熔断 + Fallback（已设计） |
| P0 | 向量检索无索引全表扫描 | 建 ivfflat 索引（建表时必须创建） |
| P1 | 对话消息深分页 | 游标分页（禁止 LIMIT offset） |
| P1 | N+1 查询 | MyBatis-Plus 批量查询，禁止循环单查 |
| P2 | 连接池耗尽 | HikariCP 配置：maximumPoolSize=20，connectionTimeout=3000ms |
| 延后 | 静态资源未压缩 | Nginx gzip，流量大时处理 |
| 延后 | JVM GC 停顿 | G1GC 已启用，暂不调优 |

---

## 系统核心链路与风险地图（2026-10-14 快照）

> 范围：hify-common / model / agent / workflow / conversation 五模块。
> knowledge、mcp 仅有空壳与结构校验，Resilience4j 熔断重试虽在本文档已设计但**尚未启用**。

### 核心链路清单

1. **普通对话流式链路（最高频）**
   `ChatController.sendMessage` → `ChatServiceImpl.sendMessage/doChat/runNormalChat`（请求线程只建 `SseEmitter`，任务提交 `llmStreamExecutor`）→ `LlmStreamServiceImpl` → `LlmStreamClient`（OkHttp SSE，readTimeout=0）→ delta 逐段回推 + assistant 消息落库。
   为什么核心：唯一面向终端用户的实时链路，3-30s 长连接，串起线程池、外部 HTTP、SSE、MySQL 四类资源；任何一处终态没收好都会挂占一条流式线程。

2. **工作流触发对话链路**
   `ChatServiceImpl.runWorkflowChat` →（仅经 workflow 的 api 包）`WorkflowRunServiceImpl.start`（`llmExecutor` + 180s `.get` 兜底）→ `WorkflowEngine` 图解释器 + `NodeExecutorRegistry`（Start/Llm/Condition/End 四个执行器）→ LLM 节点调 `LlmChatService/LlmChatClient`（非流式）→ run/node_run 终态 CAS 落库 → 结果整段一个 delta 回推。
   为什么核心：模块 DAG 的最宽汇聚点（conversation → workflow → model），编排、变量池、条件路由、模板渲染、并发收尾全部集中于此。

3. **工作流定义与图校验链路**
   `WorkflowController` → `WorkflowServiceImpl` create/update（workflow+nodes+edges **三表同事务**，更新＝旧节点逻辑删+批量插新）→ `NodeConfigParser`（sealed `NodeConfig` 多态 + jakarta 校验）→ `WorkflowGraphValidator`（唯一起止、端点存在、分支 handle、可达性、无环）。
   为什么核心：引擎执行正确性的前置防线；脏图一旦入库，失败只会在运行期以节点 FAILED 形式暴露给终端用户。

4. **Agent 配置与跨模块绑定链路**
   `AgentController` → `AgentServiceImpl` create/update（`@Transactional`）→ 经 api 包校验模型（`ModelConfigService`）与工作流（`WorkflowService.existsEnabled`）→ 知识库/MCP 关联表**整体替换**。
   为什么核心：Agent 是对话两条分支的路由配置源；绑定失效（模型停用、工作流删除）会直接让用户对话报错。

5. **模型提供商凭据与调用装配链路**
   `ProviderServiceImpl`（出参 `apiKey` 脱敏为 `******`）→ `LlmRequestSupport` 统一加载 provider/model、拼 endpoint 与 `Authorization: Bearer` → 被 `LlmChatClient`/`LlmStreamClient` 共用。
   为什么核心：所有 LLM 流量的唯一鉴权与出口；密钥处理或请求构造出错影响全部对话与工作流。

### 风险集中区域

| 风险点（类/方法） | 类型 | 可能的失败场景 |
|---|---|---|
| SSE worker 终态矩阵：`ChatServiceImpl.doChat/sendJson/safeSendError`、`llmStreamExecutor`（30/80/队列50/AbortPolicy） | 并发、资源耗尽 | ① worker 新增分支漏 catch → emitter 悬挂到 200s 超时才释放，**worker 内异常不经 `GlobalExceptionHandler`**，HTTP 转换兜底完全失效；② 客户端断开未感知，上游 LLM 继续生成白烧 token（依赖 onDelta send 失败 → `ChatClientAbortedException` → client cancel 这条链）；③ 池满拒绝时若未在请求线程直接回 error，前端拿到静默挂死的流 |
| 外部 LLM 调用：`LlmStreamClient`（readTimeout=0）、`LlmChatClient`（120s） | 性能、可用性 | 慢调用/挂死占满流式池引发雪崩；现网无熔断、无重试、无 fallback（Resilience4j 未启用），上游故障 100% 透传为对话失败；当前账号四个模型全部 404，成功链路长期未被真实流量验证 |
| 工作流并发收尾：`WorkflowRunServiceImpl` 180s 超时与 `WorkflowRunRepository` CAS、`WorkflowEngine` | 数据一致性、并发 | 超时后 HTTP 返回失败但**引擎线程不中断**，事后自行 CAS 成 SUCCEEDED，造成「接口报错但执行记录成功」；两个并发 run 终态 CAS 竞争；严格模板语义下 END 引用未产出变量（如演示工作流 3 的 `{{presale.answer}}` 对售后/技术支持分支）必然 FAILED |
| 手写表达式/模板：`ConditionEvaluator`、模板渲染器 | 正确性 | 迷你求值器仅支持 ==/!= 与有限类型，数字/字符串/布尔边界（如 `1` vs `1.0`、引号转义、null 变量）易误路由；模板严格解析，节点配置错一个引用整单失败（有意设计，但出错面大） |
| 多表事务与整体替换：`AgentServiceImpl`（关联表替换）、`WorkflowServiceImpl`（三表更新不 diff） | 数据一致性 | 三表写入中途失败依赖事务回滚（无集成测试验证）；每次编辑累积 `deleted=1` 节点/边行；MyBatis-Plus 坑已踩过：可空外键列须 `FieldStrategy.ALWAYS` 才能解绑、JSON 列必须走实体更新而非 UpdateWrapper.set |
| 凭据存储：`Provider.authConfig`（`{"apiKey":"sk-xxx"}` **明文入库**，仅出参脱敏） | 安全 | 库表/备份泄露即密钥泄露；日志误打 authConfig、连接测试错误信息回显上游响应均可能间接泄密；无任何鉴权过滤器（内网前提），任何能访问 8080 的人可直接调 DELETE 和提供商接口 |
| 会话上下文与消息表：`MysqlChatContextStore`、游标仓储、`t_chat_message` | 性能、正确性 | 上下文本期 MySQL-only、硬编码最近 20 条；消息增长后若索引/分区未跟进（本文档定的按 conversation 分区尚未实施），上下文子查询变慢；游标参数被篡改可能漏数据但不越权（单会话内） |

### 测试重心

**必须覆盖（红线，缺测试不准合并）**

- `ChatServiceImpl` worker **全终态矩阵**：成功 delta+done、BizException、未预期异常、客户端中途断开（上游必须被 cancel）、线程池 RejectedExecutionException、会话/Agent 不存在与停用；并断言每种终态下 emitter 被 complete、**无孤儿 assistant 消息**、user 消息保留（当前 10 个单测已覆盖，新增分支必须同步扩）。
- `WorkflowEngine`：三条分支路由 + default、短路语义、严格模板对未产出变量 FAILED、MAX_STEPS 防环、变量池 `nodeId.key` 解析；run/node_run 的 **RUNNING→终态 CAS** 与超时后引擎线程自行收尾的竞争。
- `ConditionEvaluator`：类型边界（BigDecimal compareTo、引号、空值、非法表达式报错而非静默 false）。
- 跨模块绑定校验：模型停用/非对话模型、工作流停用/不存在；`workflowId=null` 显式解绑（`FieldStrategy.ALWAYS` 回归测试）。
- 三表/关联表事务回滚：补 `@SpringBootTest` + 真实 MySQL 的集成测试（当前全是 Mockito 单测，事务边界实际未被验证过）。
- `ProviderServiceImpl`：出参 apiKey 必脱敏；更新时 `******` 掩码必须保留旧密钥、新值必须能覆盖；增加一条「日志/异常文本不含 apiKey」的约定检查。
- 游标分页：首页、翻页、空页、size 越界（1～100）边界；消息历史 DESC 取近后反转的顺序断言。
- LLM 客户端：用 MockWebServer/WireMock 覆盖 SSE 分帧（半包、`data:[DONE]`、usage 在末块）、非 2xx 错误体透传、4xx 不重试。

**高性价比，建议补**

- `WorkflowGraphValidator` 全规则表驱动测试（每个错误码一条脏图）。
- `IndexCoverageTest`：本文档已定 CI 策略但未落地，优先覆盖 t_chat_message 上下文查询与两条游标查询的 EXPLAIN。
- SSE 契约的前端测试：fetch 流解析对半包帧、多 `data:` 行、非 JSON 帧的容错。

**可以先跳过**

- 纯参数绑定的 Controller 单测、MapStruct PO↔DTO 映射、`HealthController`、前端样式与纯展示逻辑。
- knowledge / mcp 空模块、Redis 上下文实现、Resilience4j 熔断重试（代码未启用，等落地时再随功能补测试）。
- 压测可延后到真实模型开通后：当前 Ark 全 404，成功链路的延迟/线程池容量数字没有测量意义。


---

## 单元测试规范

> 配套「系统核心链路与风险地图」执行：测试资源优先投向五条核心链路与七类风险点，
> 不为凑覆盖率写无断言/只断言 mock 的测试。技术栈：JUnit 5 + Mockito + AssertJ。

### 必须写单元测试的代码

- **domain 层全部业务分支**，尤其是五条核心链路的决策逻辑：
  - `ChatServiceImpl`：worker 全终态矩阵（delta+done、BizException、未预期异常、客户端断开、线程池拒绝、会话/Agent 不存在或停用），并断言每条出口都 complete emitter、user 消息保留、assistant 仅成功落库；
  - `WorkflowEngine` 及四个 `NodeExecutor`、`ConditionNodeExecutor` 短路/default 路由、`ExecutionContext` 变量池（`nodeId.key`、未产出变量严格失败、MAX_STEPS 防环）；
  - `ConditionEvaluator`：==/!=、BigDecimal 归一、字符串/布尔、空值与非法表达式；
  - `NodeConfigParser` / `WorkflowGraphValidator`：sealed 多态反序列化、每条校验规则一个反例；
  - `AgentServiceImpl`：绑定校验（模型/工作流停用或不存在）、`workflowId=null` 显式解绑、关联表整体替换；
  - `ProviderServiceImpl`：apiKey 脱敏（`******`）与掩码回填（编辑时不传新密钥必须保留旧值）；
  - 模板渲染器：严格解析、未产出变量直接抛 BizException。
- **修 bug 必须先补复现测试**，同一 PR 转绿交付。
- 新增核心链路分支必须同步扩测试；测试与功能代码同一 PR。

### 不写单测、改用集成/契约测试的代码

| 代码 | 测试方式 |
|---|---|
| Mapper / 真实 Wrapper / JSON 列 TypeHandler / FieldStrategy 解绑 | 真实 MySQL 集成测试 |
| `@Transactional` 多表写入（workflow 三表、agent 主表+两张关联表、删会话 session+message） | 真实 MySQL，断言中途失败整体回滚 |
| `LlmStreamClient` / `LlmChatClient`（SSE 分帧、半包粘包、`data:[DONE]`、usage、非 2xx、回调异常 cancel） | MockWebServer 契约测试 |
| SSE 端到端 | @SpringBootTest + 流式断言 |
| Controller `@Valid` | Web 切片，只验状态码与 message |
| 游标顺序、索引命中 | 真实 MySQL；EXPLAIN 断言 |

集成测试类名以 `IntegrationTest` 结尾；不依赖外网、真实 Key、Redis。

### 命名与结构

- 方法名：`should_[期望结果]_when_[输入条件]`（新增测试强制；现有 125 个不批量改名）；
- Given-When-Then 三段，注释标出；一个测试只验一个行为；
- 公共准备用具名工厂方法（`enabledAgent(id, workflowId)`），不复制对象构造。

### Mock 与断言

- mock 边界 = 进程边界与跨模块 api 边界；不 mock 被测类自身、record/值对象、纯函数；
- 默认严格存根，公共桩才允许 `lenient()`；外部 HTTP 用 MockWebServer；
- 验证顺序用 `InOrder`；captor 必须配套断言；
- 统一 AssertJ；异常断言同时匹配类型与业务 message；集合用 `containsExactly`/`extracting`；落库实体与跨层 DTO 内容用 ArgumentCaptor（必须配套断言）；
- 至少一个有意义断言；「没异常就是通过」仅限冒烟型测试且需注释说明。

### 禁止事项（红线）

1. 禁止 sleep 等异步、空 catch、`printStackTrace`；用同步 executor / CountDownLatch/Awaitility；
2. 禁止依赖执行顺序、共享可变静态状态、测试间互相依赖；
3. 禁止测试数据出现真实 Key/密码（统一 `sk-test-xxx`）；
4. 禁止为变绿放宽生产代码校验、删断言；
5. 禁止用 null 表示空集合（生产与测试同规，返回 `List.of()`）；
6. 禁止单测启动 Spring 容器测业务分支（慢且脆）；容器只留给集成测试；
7. 禁止跨模块 import 其他模块 domain/infra 类搭测试（同生产架构红线）；
8. Provider 类测试必须含一条「密钥不泄漏」断言（日志/异常/DTO 三处）；
9. 禁止带无 issue 号的 `@Disabled` 合入主干；TODO 型空测试不计覆盖。

---

## 集成测试清单（2026-10-15 规划）

> 配套「单元测试规范」：domain 分支归 Mockito 单测；从 Controller 到真实 MySQL 的完整链路归集成测试。
> 范围：Spring Boot Test + MockMvc，LLM 用 MockWebServer 替掉，测试 Hify 自身。MCP Server 因 mcp 模块仍是空壳，本轮不预写。
> 现状（2026-10-15）：130 个测试全为 Mockito 单测，**零集成测试基建**，多表事务、JSON 列、游标、SSE 分帧全部零真实环境验证。

### 基建约定（P0 用例开工前先搭）

1. **位置与标记**：集成测试统一放 `hify-bootstrap/src/test`（唯一能装配全模块的模块），类名以 `IntegrationTest` 结尾，打 `@Tag("integration")`；surefire 默认排除、`mvn test -Dgroups=integration` 或单独 profile 才跑——无 MySQL 环境不会拖挂普通 `mvn test`。
2. **容器与请求**：`@SpringBootTest(webEnvironment = MOCK)` + MockMvc；SSE 用 `request.asyncStart()` 做异步派发断言（P2-1 才需要 RANDOM_PORT 真容器）。
3. **数据库**：独立 schema `hify_test`（`application-test.yml`，hify/hify123）；汇总各模块 `db/schema.sql` 建表；**同步用例**靠 `@Transactional` 自动回滚，**异步用例**（SSE worker / 工作流 worker 不在测试事务内）用 `@AfterEach` 或 `@Sql` 二创清理。
4. **异步变同步**：test profile 下用 `@TestConfiguration` 把 `llmExecutor`、`llmStreamExecutor` 替换为同线程执行器，消除 sleep 与时序 flaky（红线第 1 条）。
5. **Mock LLM**：引入 `okhttp3:mockwebserver`（test scope）；种子数据里 provider 的 baseUrl 指向 MockWebServer，key 用 `sk-test-xxx`；可回放服务器收到的请求（验鉴权头、消息数组）。
6. **数据与隔离**：不依赖外网、真实 Key、Redis；每个用例自洽（红线第 2 条）。

### P0 — 核心链路，必须覆盖（7 条）

| # | 测什么 | 验证什么 | 对应风险 |
|---|---|---|---|
| P0-1 | **普通对话 SSE 成功全链路**：建会话 → 发消息，MockWebServer enqueue 多 chunk 流（delta 分帧 + 末块 usage + `[DONE]`） | ①响应 text/event-stream；②delta 聚合 + done 帧 finishReason/latencyMs；③JDBC 直查 user+assistant 两行落库（tokens/finish_reason/latency_ms）；④首条消息后 session.title=正文前 20 字；⑤回放 LLM 请求：Bearer 头、systemPrompt 注入、消息顺序 | 核心链路 1；Ark 全 404，成功路径零真实流量验证 |
| P0-2 | **SSE 失败终态**：MockWebServer 分别返回 404、500 | error 帧 + emitter 立即 complete（不挂到 200s）；**无孤儿 assistant 行，user 行保留**；同会话后续消息仍可用 | worker 不经 GlobalExceptionHandler；最易静默挂死的位置 |
| P0-3 | **工作流触发对话**：Agent 绑 start→llm→end 工作流，MockWebServer 非流式补全 | 整段一个 delta + done；`t_workflow_run` SUCCEEDED、node_run 终态与 token 统计真实落库；assistant 的 tokens/latency 取 run | 核心链路 2；同时验 LlmChatClient 真实 HTTP 解析 |
| P0-4 | **工作流三表事务**：①POST 脏图（边引用不存在节点）；②PUT 脏图；③PUT 合法图 | ①workflow/node/edge 三表零新增；②旧图完整保留（旧节点未被提前逻辑删）；③旧行 deleted=1 与新行插入同事务 | 核心链路 3；三表回滚从未被验证 |
| P0-5 | **Agent 跨模块绑定 + null 解绑**：modelConfigId/workflowId 为停用或不存在、`workflowId:null` | 四类非法 → 400 且无写入；解绑后 JDBC 查出字段真为 NULL（不是 UPDATE 把列剔除）；关联表整体替换旧 deleted=1/新插入 | `FieldStrategy.ALWAYS` 坑只有真实 MySQL 能防回归 |
| P0-6 | **Provider 凭据双契约 + 掩码回填 + 级联删**：POST→GET→PUT(******)→PUT(新key)→DELETE | JDBC 查 auth_config 为明文；所有响应明文零出现（红线第 8 条）；掩码 PUT 后鉴权头仍是旧 key；新 key 可覆盖；DELETE 级联 configs+health | 核心链路 5；写反就是静默鉴权失败或冲空密钥 |
| P0-7 | **删会话同事务级联**：删有消息的会话；故障注入让消息删除抛异常；删不存在的会话；停用 Agent 建会话 | session 与 messages 同事务 deleted=1；注入失败时 session 仍在（整体回滚）；404/业务错误码语义正确 | 核心链路 1 数据一致性尾部；user 保留红线 |

### P1 — 主要功能，应该覆盖（9 条）

| # | 测什么 | 验证什么 |
|---|---|---|
| P1-1 | 工作流试运行三接口（POST runs / 列表 / 详情），分类工作流三种意图各跑一次（**先修工作流 3 收敛缺陷**） | 用 node_run 断言命中分支；LLM 404 时 run FAILED + 错误落库 |
| P1-2 | **上下文多轮窗口**：同会话连发 3+ 条，第 3 条时回放 LLM 请求体 | messages 恰为最近 20 条、**升序（旧→新）**、systemPrompt 在前——即教程 IT-P0-03 的 Hify 版 |
| P1-3 | 会话/消息两套游标分页 | 首页 nextCursor、翻页无重漏、空页、size=1/100 合法、size=0/101 → 400 |
| P1-4 | Provider 连通测试两端点 | 成功重置 failCount + 写 latency；失败 +1 保留 lastSuccessAt；即席测试不写健康行 |
| P1-5 | 逻辑删除与 code 唯一性 | 软删后同 code 可重建；列表/详情/options 不见已删；工作流删后 options 消失 |
| P1-6 | Agent 列表/detail/删 | 6 条装配 SQL 无 N+1（p6spy 计数）；knowledgeIds/mcpToolIds 回显；删级联两关联表 |
| P1-7 | chat-options / workflows/options | 只返回启用项，停用后接口立即消失 |
| P1-8 | `@Valid` + 错误信封（表驱动一组） | 400 + 统一 Result 信封；GlobalExceptionHandler 真实状态码映射 |
| P1-9 | run/node_run 终态 CAS | 预置 RUNNING 后两个并发结束动作只有一个生效 |

### P2 — 边缘场景，有余力再做（6 条）

1. 客户端中途断开（RANDOM_PORT + client abort）：MockWebServer 侧上游被取消、无 assistant 行；
2. 工作流 180s 超时与引擎线程自行收尾的终态竞争（test profile 调小超时，单测数十秒）；
3. SSE 帧格式细节（`data:` 前缀、空行分隔、非 JSON 帧容错）——优先在 MockWebServer 客户端契约测试里做；
4. 同会话两条并发消息不串台（真实线程池 + CountDownLatch）；
5. 超长 content（MEDIUMTEXT）/emoji/引号中文原样往返；
6. 绕过图校验直插脏数据的运行期防御（MAX_STEPS、不可达节点）。

**明确不做**：鉴权/越权（内网前提已接受）；knowledge/mcp（空壳，落地随功能补）；HealthController、MapStruct 映射（规范已列跳过）；Resilience4j（未启用）。

### 教程 IT-P0-03（上下文多轮）在 Hify 的差异

教程设计为「先写红测试 → 修 `ORDER BY created_at ASC LIMIT` → 变绿」。**该 bug 在 Hify 初版即写对**（`id DESC LIMIT 20` 取最近 + 内存反转为升序），因此 P1-2 在 Hify 是**回归测试，首次即绿**，不制造红→修→绿过程；若未来该用例变红，说明 DESC/反转回归，按「修 bug 先补复现测试」同规处理。

### 落地顺序

P0-6（链路最短，磨基建）→ 基建 + P0-1/P0-2（MockWebServer、同步 executor 一次到位，顺手 P1-2）→ P0-3/P0-4/P1-1（工作流簇，先合入工作流 3 收敛修复）→ P0-5/P0-7 → P1 其余。预计 P0 七条 2-3 天。
