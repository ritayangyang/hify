# 工作流管理 API

工作流定义（开始 → LLM → 条件分支 → 工具调用 → 知识检索 → 结束）的 CRUD，以及同步试运行与执行记录查询。
模块：`hify-workflow`，表：`t_workflow` / `t_workflow_node` / `t_workflow_edge`。

存储模型为三张表整体维护：

- 创建：workflow 写 1 条 + nodes 批量插入 + edges 批量插入，**同一事务**，任一失败全部回滚
- 更新：基本信息整体替换；旧 nodes/edges **逻辑删除后批量插入新的，不做 diff**
- 删除：三张表同步逻辑删除
- 详情：从三张表组装回完整图返回

---

## 1. 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/workflows` | 创建工作流（完整请求体） |
| GET | `/api/v1/workflows` | 分页查询列表（不含图） |
| GET | `/api/v1/workflows/{id}` | 查询详情（完整节点和边） |
| PUT | `/api/v1/workflows/{id}` | 更新工作流（整体替换） |
| DELETE | `/api/v1/workflows/{id}` | 逻辑删除 |
| GET | `/api/v1/workflows/options` | 已启用工作流下拉选项（id/name，供 Agent 绑定） |
| POST | `/api/v1/workflows/{id}/runs` | 同步试运行一次（返回时执行已结束） |
| GET | `/api/v1/workflows/{id}/runs` | 某工作流的执行记录分页 |
| GET | `/api/v1/workflows/runs/{runId}` | 执行记录详情（含每个节点明细） |

## 2. 图结构

### 2.1 节点 Node

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 图内节点业务 id（`start`/`n2`…），工作流内唯一 |
| type | string | 是 | `START` / `LLM` / `CONDITION` / `TOOL` / `KNOWLEDGE` / `END`（大小写不敏感） |
| config | object | 否 | 按 type 多态的节点配置，见 2.3；缺省按 `{}` 处理 |
| position | object | 否 | 画布坐标 `{x, y}`，后端不解释，原样存取 |

### 2.2 连线 Edge

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 否 | 连线业务 id（前端生成） |
| source | string | 是 | 起点节点 id |
| sourceHandle | string | 条件必填 | 条件分支 id（与 config.branches[].id 对应）或 `"default"`；普通节点唯一出线不传 |
| target | string | 是 | 终点节点 id |

### 2.3 节点配置 config（按 type 多态）

| type | 字段 | 约束 |
|------|------|------|
| START | `inputs: string[]` | 入参字段名声明，可空 |
| LLM | `modelConfigId: long` | 必填，必须为**已启用的对话模型**（model 模块校验，批量一次） |
|  | `promptTemplate: string` | 必填，支持 `{{nodeId.field}}` 引用 |
|  | `temperature: number` | 选填，0～2 |
|  | `maxTokens: int` | 选填，≥1 |
|  | `outputKey: string` | 必填，结果写入小黑板的键名 |
| CONDITION | `branches: [{id, expression}][]` | 至少 1 个分支；分支 id 节点内唯一；按顺序命中，均不命中走 `default` 边 |
| TOOL | `mcpToolId: long` | 必填（mcp 模块落地前只做结构校验） |
|  | `args: object` | 入参映射，值可写模板 |
| KNOWLEDGE | `knowledgeIds: long[]` | 至少 1 个 |
|  | `query: string` | 必填，支持模板 |
|  | `topK: int` | 选填，1～20 |
| END | `replyTemplate: string` | 必填，最终回复模板 |

解析由后端 `NodeConfigParser` 完成：Jackson sealed 多态反序列化为强类型 record
（`NodeConfig` permits 6 个 record），再做 Bean Validation；未知类型/结构非法/字段缺失均返回 400。

### 2.4 图语义校验（保存时）

- 节点 id 非空且唯一
- 有且仅有一个 START、一个 END
- 连线 source/target 必须存在，禁止节点连接自身
- START 恰好 1 条出线；END 不允许出线；其他非条件节点至多 1 条不带 handle 的出线
- CONDITION 每条出线必须带 `sourceHandle`，handle 必须命中已声明分支或 `default`，每个 handle 至多 1 条出线，每个声明分支都必须有出线
- 所有节点必须从 START 可达
- 不允许环路（MVP 为顺序执行）

## 3. 接口详情

### 3.1 创建工作流

`POST /api/v1/workflows`

请求体：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 名称，活跃记录范围内唯一 |
| description | string | 否 | 简介 |
| status | int | 否 | 0 禁用 / 1 启用，缺省 1 |
| nodes | Node[] | 是 | 不允许空数组 |
| edges | Edge[] | 是 | 不允许空数组 |

```bash
curl -X POST http://localhost:8080/api/v1/workflows \
  -H 'Content-Type: application/json' -d '{
  "name": "客服意图分流",
  "description": "识别退货/投诉意图并调用工具",
  "nodes": [
    {"id": "start", "type": "START", "config": {"inputs": ["userMessage"]}},
    {"id": "n2", "type": "LLM",
     "config": {"modelConfigId": 6, "promptTemplate": "识别意图，只输出：退货/投诉/闲聊",
                "temperature": 0, "outputKey": "intent"}},
    {"id": "c1", "type": "CONDITION",
     "config": {"branches": [{"id": "b1", "expression": "{{n2.intent}} == '\''退货'\''"}]}},
    {"id": "n3", "type": "TOOL", "config": {"mcpToolId": 11, "args": {}}},
    {"id": "end", "type": "END", "config": {"replyTemplate": "处理结果：{{n3.result}}"}}
  ],
  "edges": [
    {"source": "start", "target": "n2"},
    {"source": "n2", "target": "c1"},
    {"source": "c1", "sourceHandle": "b1", "target": "n3"},
    {"source": "n3", "target": "end"},
    {"source": "c1", "sourceHandle": "default", "target": "end"}
  ]
}'
```

响应 `data` 为详情结构（3.3）。

### 3.2 分页查询

`GET /api/v1/workflows?page=1&size=20&name=客服&status=1`

| 参数 | 说明 |
|------|------|
| page / size（或 pageSize） | 分页，size 1～50 |
| name | 名称模糊匹配 |
| status | 0/1 精确匹配 |

`data.records[]` 每行：

```json
{
  "id": 2,
  "name": "客服意图分流",
  "description": "…",
  "status": 1,
  "nodeCount": 3,
  "edgeCount": 2,
  "createdAt": "2026-10-13T22:38:30.799",
  "updatedAt": "2026-10-13T22:40:11.123"
}
```

节点/连线数量由两条 IN 批量查询内存分组装配，列表固定 4 条 SQL（count + 主表 + 节点计数 + 连线计数），无 N+1。

### 3.3 查询详情

`GET /api/v1/workflows/{id}`

```json
{
  "workflow": {"id": 2, "name": "客服意图分流", "description": "…", "status": 1,
               "createdAt": "…", "updatedAt": "…"},
  "nodes": [
    {"id": "start", "type": "START",
     "config": {"inputs": ["userMessage"]}, "position": {"x": 80.0, "y": 160.0}},
    {"id": "n2", "type": "LLM",
     "config": {"modelConfigId": 6, "promptTemplate": "…", "temperature": 0,
                "maxTokens": 512, "outputKey": "intent"},
     "position": null}
  ],
  "edges": [
    {"id": "e1", "source": "start", "sourceHandle": null, "target": "n2"},
    {"id": "e3", "source": "c1", "sourceHandle": "b1", "target": "n3"}
  ]
}
```

- 节点/连线按保存顺序（数据库 id 升序）返回
- `config` 按 `type` 多态序列化，出参中不重复携带 `type` 字段
- 不存在返回 `404 工作流不存在: {id}`

### 3.4 更新工作流

`PUT /api/v1/workflows/{id}`，请求体字段与创建完全一致（全量）。

- `name` 为空保留旧名称，传值则校验重名（排除自身）
- `status` 为空保留旧状态
- `description` 传 null 即清空
- 旧节点/连线整批逻辑删除，按新请求批量插入；历史版本在库中表现为 `deleted=1` 的旧行，不做 diff、不做版本管理

### 3.5 删除工作流

`DELETE /api/v1/workflows/{id}` → `{ "code": 200, "data": null }`。
工作流、节点、连线在同一事务内全部逻辑删除。

## 4. 错误响应

| HTTP | 典型 message |
|------|--------------|
| 400 | `name: name 不能为空; nodes: nodes 不能为空`（@Valid 字段校验，多字段 `; ` 拼接） |
| 400 | `工作流名称已存在: xxx` |
| 400 | `未知节点类型: XXX` |
| 400 | `节点 n2 配置解析失败: …` / `节点 n2 配置校验失败: promptTemplate promptTemplate 不能为空` |
| 400 | `节点 id 重复: n2` / `缺少开始节点（START）` / `结束节点有且只能有一个` |
| 400 | `连线终点不存在: ghost` / `节点不能连接自身: n2` |
| 400 | `非条件节点只能有一条出线: n2` / `结束节点不能有出线: end` |
| 400 | `条件节点 c1 的连线指向了不存在的分支: bX` / `条件分支没有配置出线: c1/b2` |
| 400 | `存在不可达节点: orphan` / `工作流存在环路: c1 → n2 → c1` |
| 400 | `LLM 节点 n2 绑定模型不存在: 9999` / `…已停用: 豆包…` / `…不是对话模型` |
| 400 | `请求体格式错误或参数值非法`（JSON 语法错误） |
| 404 | `工作流不存在: 999` |

## 5. 试运行与执行记录

执行期两张表 `t_workflow_run`（一次执行）与 `t_workflow_node_run`（一个节点一次执行）：
先插 RUNNING，节点结束/整次结束各自独立 UPDATE 为终态 CAS（仅当 status=RUNNING 可改终态），不套大事务。

### 5.1 触发执行

`POST /api/v1/workflows/{workflowId}/runs`

请求体可省略（等价空入参）：

```json
{ "inputs": { "userMessage": "我要退货" } }
```

执行语义：

- **同步等待**：HTTP 工作线程把任务提交到 `llmExecutor` 线程池（20~50 线程，队列 100，CallerRunsPolicy）后等待，**总体兜底超时 180 秒**；超时返回 500，节点后续若自行完成会经 CAS 落 SUCCEEDED（运行中记录可事后查询）
- **严格模板渲染**：`{{nodeId.key}}` 引用了未产出的变量（上游不在执行路径上、没执行或没有该输出）→ 该节点直接失败，整次执行 FAILED，**绝不带着占位符调用模型**
- **条件路由**：分支按配置顺序求值，第一个为真者出线；全部未命中走 `sourceHandle=default` 的边；未配 default 则整次执行 FAILED
- **步数兜底**：最多 50 步（保存期已禁环，只防异常数据）
- **当前支持节点**：START / LLM / CONDITION / END；TOOL / KNOWLEDGE 运行期命中报错 `…暂不支持执行（对应模块尚未交付）`
- **LLM 错误透传**：上游 4xx/5xx、网络错误、空返回统一转 `BizException`（4xx→400，5xx/IO→500），错误原文（截断 500 字）落节点记录并写入 HTTP 响应
- 工作流不存在→404；已禁用→400；缺少 START 声明的必填入参→400（插执行记录之前拦截，不产生脏记录）

成功返回（`code=200`）即执行详情，结构同 5.3。失败由全局异常处理返回错误响应，但**执行记录与已完成节点记录均已落库**（可在记录里看到具体卡在哪个节点）。

### 5.2 执行记录分页

`GET /api/v1/workflows/{workflowId}/runs?page=1&size=20`（size 1~50，默认 20）

按 id 倒序（最新在前），只含 run 概要，不含节点明细，包装在标准 `PageResponse`（records/total/current/size）里。

### 5.3 执行记录详情

`GET /api/v1/workflows/runs/{runId}` → 404 `执行记录不存在: {runId}`

```json
{
  "run": {
    "id": 18,
    "workflowId": 3,
    "status": "SUCCEEDED",
    "inputs": { "userMessage": "你们产品有什么功能？" },
    "output": "您好，本产品……",
    "errorMessage": null,
    "elapsedMs": 4230,
    "totalTokens": 1876,
    "createdAt": "2026-10-14T10:02:11.123",
    "finishedAt": "2026-10-14T10:02:15.353"
  },
  "nodeRuns": [
    {
      "id": 101, "runId": 18, "nodeId": "start", "nodeType": "START",
      "status": "SUCCEEDED",
      "inputs": { "userMessage": "你们产品有什么功能？" },
      "outputs": { "declaredInputs": ["userMessage"] },
      "errorMessage": null, "elapsedMs": 0,
      "promptTokens": null, "completionTokens": null,
      "createdAt": "2026-10-14T10:02:11.124", "finishedAt": "2026-10-14T10:02:11.124"
    },
    {
      "id": 102, "runId": 18, "nodeId": "classify", "nodeType": "LLM",
      "status": "SUCCEEDED",
      "inputs": { "modelConfigId": 5, "prompt": "判断用户问题类型……" },
      "outputs": { "intent": "售前" },
      "errorMessage": null, "elapsedMs": 820,
      "promptTokens": 120, "completionTokens": 2,
      "createdAt": "2026-10-14T10:02:11.125", "finishedAt": "2026-10-14T10:02:11.945"
    }
  ]
}
```

**run 字段**

| 字段 | 说明 |
|------|------|
| status | `RUNNING` / `SUCCEEDED` / `FAILED`（同步接口正常返回时只剩两种终态；180s 超时瞬间可能仍是 RUNNING） |
| inputs | 触发入参（key 做了 trim） |
| output | END 节点渲染后的回复；FAILED 为 null |
| errorMessage | 失败原因，最长 500 字 |
| elapsedMs | 从执行开始到终态的总耗时 |
| totalTokens | 路径上所有 LLM 节点 prompt+completion token 合计；无 LLM 节点为 null |

**nodeRun 字段**

| 字段 | 说明 |
|------|------|
| 顺序 | 数组按 id 升序即实际执行顺序；分支场景只包含**实际走过的节点**（未命中的分支没有记录） |
| inputs | 节点实际输入：LLM 为 `{modelConfigId, prompt（渲染后）}`，CONDITION 为每个分支 `表达式渲染结果 => true/false`，START 为触发入参，END 为 `{}` |
| outputs | 实际写入变量池的键值（LLM 为 `{outputKey: 文本}`，CONDITION 为 `{matchedBranch: 分支id/null}`，END 为 `{reply: 最终输出}`） |
| promptTokens/completionTokens | 仅 LLM 节点有值，来自上游 usage |
| status/errorMessage | 某节点失败时该节点 FAILED 并带错误原文，其后节点不再执行，run 同步 FAILED |

### 5.4 试运行新增错误响应

| HTTP | 典型 message |
|------|--------------|
| 400 | `工作流入参缺少字段: userMessage`（可多个，`, ` 拼接） |
| 400 | `工作流已禁用，无法执行: xxx` |
| 400 | `节点 n2 模板引用了未产出的变量: presale.answer（上游节点未执行或没有该输出）` |
| 400 | `条件节点 router 所有分支均未命中，且未配置 default 出线` / `条件节点 router 命中的分支 b2 没有配置出线` |
| 400 | `节点 tool1 的类型 TOOL 暂不支持执行（对应模块尚未交付）` |
| 400 | `LLM 调用失败(HTTP 404): The model or endpoint … does not exist…`（上游错误原文透传；401/400 等 4xx 同型） |
| 500 | `工作流执行超时（180s），可稍后在执行记录中查看结果`（引擎线程不中断，事后可能自行 CAS 落 SUCCEEDED） |
| 500 | `LLM 网络调用失败: …` / `LLM 调用失败(HTTP 503): …` / `执行步数超过 50，疑似存在环路，已终止` / `连线指向不存在的节点: n9` |
| 404 | `工作流不存在: 999` / `执行记录不存在: 999` |

## 6. 已启用工作流下拉选项

`GET /api/v1/workflows/options`，无参，返回所有 `status=1` 的工作流，按 id 升序，供 Agent 表单「绑定工作流」使用：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 2, "name": "客服意图分流-精简版" },
    { "id": 3, "name": "智能客服分类工作流" }
  ]
}
```

Agent 绑定/解绑：`PUT /api/v1/agents/{id}` 显式传 `workflowId`，`null` 即解绑；绑定时校验工作流必须启用，否则 `400 绑定工作流不存在或已停用: {id}`。

## 7. 修订记录

| 日期 | 内容 |
|------|------|
| 2026-10-13 | 初版：工作流 CRUD 五接口；三表同事务整体写入/替换；NodeConfig sealed 多态 + NodeConfigParser；图语义校验（唯一起止、端点、分支 handle、可达性、无环） |
| 2026-10-14 | 执行引擎 + 试运行三接口：同步图解释器（START/LLM/CONDITION/END）、严格模板渲染、分支/default 路由；`llmExecutor` 线程隔离 + 180s 兜底；t_workflow_run / t_workflow_node_run 独立落库与终态 CAS；model 模块新增 LlmChatService（OpenAI 兼容非流式） |
