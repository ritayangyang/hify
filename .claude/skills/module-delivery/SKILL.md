---
name: module-delivery
description: Hify 单个业务模块从选型设计到前后端全流程交付的标准 SOP（咨询设计 → schema.sql → 后端分层 → 前端对接 → 真实链路验收）。当用户要"做一个新模块/新功能""按模块流程开发""端到端交付 XX 模块"，或进入多步骤功能开发时使用。
---

# Hify 模块交付流程

适用：一个有数据存储、有 CRUD/业务逻辑、需要前后端联动的业务模块（如 Provider、Agent、知识库）。
不适用：纯文案/样式修改、单点 bug 修复（直接改 + 验证即可）。

**铁律**

- 每个阶段必须有明确产出物和验证方式，验证不过不进下一阶段。
- 标注 ⏸️ **等待用户确认** 的决策点，必须停下等用户拍板，不替用户做决定。
- 后端由我在后台任务拉起（用户不自行启动）；Key/密码等敏感信息绝不写日志、不出现在接口出参。

---

## 阶段 0｜咨询模式：选型、数据模型、边界

**做什么**（只讨论不写代码）：

1. 选型：用什么协议/第三方服务、是否 MVP 范围内、哪些功能明确砍掉。
2. 数据模型：表、关键字段、枚举、索引草案、与其他模块的关系。
3. 边界：四层各自放什么、跨模块通过哪个 `api/` 接口调用、什么是派生字段（不落库）。

**产出物**：对话内的设计结论（表结构草案、枚举值、接口清单、跨模块依赖关系）。

**验证方式**：用户确认设计结论。

⏸️ **等待用户确认**：数据模型（表/字段/枚举/索引）定稿。定稿后才允许改 schema.sql。

---

## 阶段 1｜数据模型落地：schema.sql

**做什么**：

- 按项目数据库规范建表：`t_` 前缀、snake_case、必备 `id/created_at/updated_at/deleted`、utf8mb4。
- 索引遵循最左前缀，`deleted` 纳入组合索引；每表 ≤5 个索引；TEXT 不建普通索引。
- 本地库执行 DDL（已有库表变更用 `ALTER TABLE`，让 schema.sql 与真实库保持一致）。

**产出物**：更新后的 `schema.sql` + 本地 MySQL 中实际创建/变更的表。

**验证方式**：`SHOW CREATE TABLE` 确认字段、字符集、索引与设计一致。

**注意事项（踩过的坑）**：

- JDBC URL 的 `characterEncoding` 必须是 **`UTF-8`**，写 `utf8mb4` 会被 connector-j 9.x 当成 Java 字符集，所有接口直接 500。
- JSON 列设了 `NOT NULL` 时，MyBatis-Plus 插入 null Map 会被跳过导致 `Field 'xxx' doesn't have a default value`；要么列给默认值，要么 Service 层把 null 归一化成 `Map.of()`。
- **不要建 `UNIQUE(code, deleted)` 这类软删唯一索引**：deleted 只有 0/1，同一 code 第二次删除时两条记录变成 `(code,1)` 冲突。活跃数据唯一性放应用层保证，库里用普通索引。

---

## 阶段 2｜后端分层开发（严格按顺序，每层验证后再进下一层）

顺序：**Enum/Entity + Mapper → DTO（含 Request/Response）→ Service（domain）→ Controller（web）**。
模块内四层目录固定为 `api / domain / infra / web`；跨模块只准依赖对方的 `api` 包。

### 2.1 Entity + Mapper（infra）

- PO 用 `Po` 后缀，Mapper 用 `Mapper` 后缀；逻辑删除字段加 `@TableLogic`，JSON 字段用 JacksonTypeHandler。
- Repository 接口在 domain、实现在 infra，PO ↔ 领域对象转换不泄漏到上层。

**验证**：`mvn -pl <module> compile` 通过。

### 2.2 DTO（api 包）

- DTO 用 record；跨模块传递只用 api 包 DTO，不传 PO/领域对象。
- 枚举考虑入参兼容：`@JsonCreator` 同时接受枚举名和数字（如 `"OPENAI"` 和 `1`），`@JsonValue` 决定出参形态。

**验证**：编译通过；枚举双格式写单测。

**注意事项**：

- 分页请求要兼容前端习惯的 `pageSize` 参数名（后端字段 `size` + `setPageSize` 别名 setter）。
- 出参里的密钥类字段必须脱敏成 `******`，在组装 DTO 时处理。
- Jackson 多态（sealed interface + `@JsonTypeInfo(EXISTING_PROPERTY)`）：**不要加 `visible = true`**，否则 type 标记会被再喂给子类 record，严格 ObjectMapper 下报 "Unrecognized field type"；测试里用 plain `new ObjectMapper()` 才能暴露这个问题（Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES 会把它掩盖）。原始类型参数打桩用 `anyLong()`，`any()` 拆箱 NPE 会污染 Mockito matcher 栈导致连锁误报。

### 2.3 Service（domain）

- 业务异常统一 `throw new BizException(ErrorCode.xxx)`，不 catch 了再包一层；事务边界在这一层。
- 列表装配类需求（如每行带健康状态、关联计数）**批量查询 + 内存分组**，固定 SQL 条数，禁止循环单查（N+1）。
- 能派生的状态不落库（如健康状态由 failCount + lastSuccessAt 推算）。
- `updated_at` 自动填充：更新时传 null 触发 fill——把旧值带回去会让 strictUpdateFill 跳过、时间戳不更新。

**验证**：单测覆盖核心分支（Mockito 即可），`mvn test` 全绿。

### 2.4 Controller（web）

- 只做参数校验（`@Valid`）、调用本模块 api 接口、返回 `Result<T>`；不写业务逻辑。
- 字符串形态的枚举参数转换失败要转成 `BizException(PARAM_ERROR)`，不能让它变 500。
- 确认 `GlobalExceptionHandler` 覆盖：非法 JSON、参数类型不匹配等返回 400 而不是 500。

**产出物**：可运行的后端接口。

**验证方式**：

1. `mvn -pl <module> test` 全绿；
2. `mvn install` 后重启后台后端（先停旧任务，确认 8080 端口释放——spring-boot:run 的 fork 子进程可能成为孤儿，用 `lsof -ti :8080` 清理）；
3. **curl 走完整接口**，不依赖前端。

**注意事项**：

- 第三方协议适配要注意 URL 拼接：baseUrl 已带版本段（`/v1`、火山方舟的 `/api/v3`）时不能再无脑补 `/v1`，用"末尾是否是版本段"判断。
- 网络不通/鉴权失败属于**业务结果**不是系统错误：连通性测试类接口无论成败都返回 200 + `{success:false,errorMessage}`。
- 第三方真实凭证（如 API Key）由用户提供，用完删除含 Key 的临时文件；日志中 grep 明文 Key 必须为 0 处。

⏸️ **等待用户确认**（可选但推荐）：后端接口契约（出入参字段、错误码）用真实 curl 跑通后，再开始前端对接，避免契约反复。

---

## 阶段 3｜前端对接

顺序：**API 文件 → 替换 mock**。

### 3.1 创建 API 文件

- `src/api/<module>.ts`：类型定义与后端 DTO 字段逐一对齐；请求方法走统一封装（拦截器已解包 `Result.data`）。
- 布尔语义参数在请求层映射成后端枚举（如 enabled → status=1/0）。

### 3.2 替换页面 mock 数据源

- 列表：`HifyTable` 的 `fetch-api` 接真实分页方法（注意适配 `{page,size}` ↔ `{page,pageSize}`）。
- 新建/编辑弹窗：按 mode 调 create/update；编辑态密钥框留空表示"不修改"。
- 删除：`useConfirm` 包真实删除接口，成功后 refresh。
- 行级动作（如连通性测试）：独立 loading 状态，结果用 ElMessage 反馈，完成后刷新派生列。

**验证方式**：

1. `npx vue-tsc --noEmit` 零错误（TS 版本锁定，勿擅自升级）；
2. 必须用**真实浏览器**验证渲染，不能只信 tsc（见下方白屏坑）。

**注意事项（全是真实踩过的）**：

- **el-tooltip 不要放在 HifyTable 动态转发插槽里**：tooltip 的 popper 异步重渲染触发节点时会丢失插槽的 `row`，`obj[row.x]` 取值 undefined 直接抛错，整个路由视图白屏（只剩导航栏）。改用元素原生 `title` 属性。
- 插槽里对枚举做映射（`TAG_MAP[row.status]`）一律加空值兜底函数，缺值降级到 UNKNOWN/默认态，绝不允许渲染期抛异常。
- 泛型 SFC 组件（HifyTable/HifyFormDialog）不要用 `InstanceType<typeof Xxx>` 取实例类型，vue-tsc 会报 TS2344；用**回调 ref + shallowRef 显式结构化类型**注册实例方法。
- SFC `<script setup>` 顶层被模板引用的可变状态用 `const x = ref()`，不要用顶层 `let`——Volar 编译模板时会给它加 `.value` 导致 TS2339。
- `useConfirm` 取消时返回 `CONFIRM_CANCELLED` 哨兵，**不能用 `result === null` 判断是否取消**：void 删除接口成功返回值也是 null，会导致删除成功后不刷新列表（Agent 模块实测踩过）；判断写法 `result !== CONFIRM_CANCELLED`。
- 页面白屏时用本机 Chrome headless 复现并抓控制台：
  `"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless --virtual-time-budget=8000 --enable-logging=stderr --dump-dom <url>`

---

## 阶段 4｜完整验收（真实链路，非 Mock）

### 4.1 后端 curl 验收

- CRUD 四件套 + 业务动作（如 test-connection），断言 HTTP 200、`code=200`、关键字段值正确。
- 用用户给出的真实契约（精确的请求体形状，如枚举传字符串、字段可缺省）逐发一遍。
- 异常路径：缺必填、坏枚举、坏 JSON → 期望 400；资源不存在 → 404。

### 4.2 数据层验收（必要时）

- 列表接口开 MySQL general_log 到 TABLE，发一次请求，数 SELECT 条数：必须**与行数无关的固定条数**（典型：count + 主表 + 两个 IN 批量装配），证明无 N+1；验完立刻 `general_log='OFF'`。
- 直接查表核对：脱敏落库/出参、逻辑删除、updated_at 是否刷新。

### 4.3 浏览器全流程验收

- headless Chrome 先过一遍（DOM 中能查到关键内容、控制台无错误），再让用户在浏览器走：列表 → 新建 → 编辑 → 删除 → 行级动作 → 筛选/分页。

**产出物**：验收结论（含真实返回示例）；如接口对外，同步更新 `docs/api/` 文档。

⏸️ **等待用户确认**：用户在浏览器完成全流程确认后，模块才算交付完成。

---

## 阶段 5｜文档与收尾

- `docs/api/<module>.md`：接口清单、字段表、枚举协议、真实 curl/响应示例、修订记录。
- 删除含密钥的临时文件；后台任务（后端/前端 stub）该停的停，后端按约定保留运行。
- 沉淀经验：通用规范进 CLAUDE.md；一次性/任务型 SOP 进 Skill；环境事实（账号、地址，不含密码值）进 memory。

## 流程速查

| 阶段 | 产出物 | 验证方式 | 闸口 |
|------|--------|----------|------|
| 0 咨询设计 | 表/枚举/接口/边界结论 | 用户确认 | ⏸️ 数据模型定稿 |
| 1 schema | schema.sql + 真实表 | SHOW CREATE TABLE | — |
| 2.1-2.2 PO/Mapper/DTO | infra + api 类 | compile + 枚举单测 | — |
| 2.3 Service | domain 逻辑 | 单测全绿 | — |
| 2.4 Controller | 可运行接口 | mvn test + curl | ⏸️ 契约确认（推荐） |
| 3 前端 | api/*.ts + 页面 | vue-tsc + 真实浏览器 | — |
| 4 验收 | 验收结论 | curl + general_log + 浏览器 | ⏸️ 用户终验 |
| 5 收尾 | docs + 清理 | — | — |
