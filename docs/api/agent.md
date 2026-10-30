# Agent 管理 API

管理 Agent 配置：名称、系统提示词、绑定对话模型、生成参数、知识库/MCP 关联。

- 模块：`hify-agent`
- Base Path：`/api/v1/agents`
- 对应表：`t_agent`、`t_agent_knowledge`、`t_agent_mcp_tool`

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agents` | 创建 Agent |
| GET | `/agents` | 分页查询 Agent |
| GET | `/agents/{id}` | 查询详情（含关联资源 id） |
| PUT | `/agents/{id}` | 更新 Agent（整体替换可编辑字段与关联） |
| DELETE | `/agents/{id}` | 删除 Agent（逻辑删除，关联同步逻辑删除） |
| GET | `/model-configs/chat-options` | 已启用对话模型下拉选项（model 模块提供） |

---

## 数据模型

### Agent 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 主键 |
| name | string | 名称，活跃记录范围内唯一 |
| description | string \| null | 简介 |
| systemPrompt | string \| null | 系统提示词 |
| modelConfigId | long | 绑定模型 `t_model_config.id`，必须为**启用中的对话模型** |
| workflowId | long \| null | 绑定工作流 `t_workflow.id`，必须启用；`null` 为普通对话。对话页发送消息时非空则走工作流分支（见 [chat.md](chat.md)） |
| modelParams | object \| null | 生成参数覆盖：`temperature` / `topP` / `maxTokens` |
| status | int | `1` 启用（默认），`0` 禁用 |
| createdAt | string | ISO-8601 |
| updatedAt | string | ISO-8601，更新时自动刷新 |

### AgentListItem（列表行）

Agent 全部字段之外附加（服务端批量装配，单次列表请求固定 SQL 条数，无 N+1）：

| 字段 | 类型 | 说明 |
|------|------|------|
| modelName | string \| null | 绑定模型展示名；模型被物理删除时为 null |
| providerName | string \| null | 模型所属提供商名称 |
| knowledgeCount | int | 关联知识库数 |
| mcpCount | int | 关联 MCP 工具数 |

### AgentDetail（详情）

| 字段 | 类型 | 说明 |
|------|------|------|
| agent | Agent | Agent 基础信息 |
| modelName / providerName | string \| null | 绑定模型摘要 |
| knowledgeIds | long[] | 关联知识库 id 全量列表（当前知识库模块未落地，本期只存不校验存在性） |
| mcpToolIds | long[] | 关联 MCP 工具 id 全量列表（同上） |

### 关联维护语义

- 创建/更新传入 id 数组，服务端**去重后整体替换**：旧关联逻辑删除，新关联插入。
- 不传或传 `null` 视同空列表——更新时会**清空**该类关联（前端编辑表单未开放多选时必须原样回传详情中的 id）。
- 删除 Agent 时两张关联表同事务逻辑删除。

---

## 1. 创建 Agent

`POST /agents`

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | **是** | 名称，活跃范围内唯一 |
| modelConfigId | long | **是** | 启用中的对话模型 id（见下拉接口） |
| workflowId | long \| null | 否 | 绑定启用中的工作流；表单始终显式提交，`null` 即解绑（null 语义区别于仅服务端内部使用的 modelConfigId） |
| description | string | 否 | 简介 |
| systemPrompt | string | 否 | 系统提示词 |
| modelParams | object | 否 | 生成参数覆盖 |
| status | int | 否 | 默认 `1` |
| knowledgeIds | long[] | 否 | 知识库 id，默认空 |
| mcpToolIds | long[] | 否 | MCP 工具 id，默认空 |

```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "客服助手",
    "description": "售前客服",
    "systemPrompt": "你是专业客服",
    "modelConfigId": 5,
    "modelParams": {"temperature": 0.7, "topP": 0.9},
    "knowledgeIds": [101, 102],
    "mcpToolIds": [201]
  }'
```

### 错误

| 场景 | HTTP | message 示例 |
|------|------|--------------|
| 缺 name / modelConfigId | 400 | `name: name 不能为空; modelConfigId: modelConfigId 不能为空` |
| 名称重复 | 400 | `Agent 名称已存在: 客服助手` |
| 模型不存在 | 400 | `绑定模型不存在: 99999` |
| 模型已停用 / 非对话模型 | 400 | `绑定模型已停用: xxx` / `绑定模型不是对话模型: xxx` |
| 绑定工作流不存在/已停用 | 400 | `绑定工作流不存在或已停用: 999`（创建/更新同型） |

## 2. 分页查询 Agent

`GET /agents`

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| page | int | 1 | ≥1 |
| size / pageSize | int | 20 | 1～50，`pageSize` 为别名 |
| name | string | — | 名称模糊匹配 |
| status | int | — | `0` 禁用 / `1` 启用 |

```bash
curl "http://localhost:8080/api/v1/agents?page=1&pageSize=10&status=1"
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "客服助手 Pro",
        "description": "售后客服",
        "systemPrompt": "你是售后专家",
        "modelConfigId": 6,
        "modelParams": null,
        "status": 1,
        "createdAt": "2026-10-13T17:06:50.635",
        "updatedAt": "2026-10-13T17:10:02.118",
        "modelName": "豆包 Seed 1.6 思考版",
        "providerName": "火山方舟",
        "knowledgeCount": 1,
        "mcpCount": 0
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

## 3. 查询 Agent 详情

`GET /agents/{id}`

```bash
curl http://localhost:8080/api/v1/agents/1
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "agent": { "id": 1, "name": "客服助手 Pro", "modelConfigId": 6, "status": 1, "...": "..." },
    "modelName": "豆包 Seed 1.6 思考版",
    "providerName": "火山方舟",
    "knowledgeIds": [103],
    "mcpToolIds": []
  }
}
```

不存在或已删除：`404`，`Agent 不存在: {id}`。

## 4. 更新 Agent

`PUT /agents/{id}`

**整体替换**：请求体为编辑表单完整内容（name/modelConfigId 必填）；`knowledgeIds`/`mcpToolIds` 也按数组整体替换。`createdAt` 保留，`updatedAt` 自动刷新。

```bash
curl -X PUT http://localhost:8080/api/v1/agents/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "客服助手 Pro",
    "description": "售后客服",
    "systemPrompt": "你是售后专家",
    "modelConfigId": 6,
    "status": 1,
    "knowledgeIds": [103],
    "mcpToolIds": []
  }'
```

成功响应同创建的 `data` 结构；名称冲突、模型非法均为 `400`；资源不存在 `404`。

## 5. 删除 Agent

`DELETE /agents/{id}`，逻辑删除，同事务逻辑删除两张关联表数据。重复删除返回 `404`。

## 6. 对话模型下拉选项

`GET /model-configs/chat-options`（model 模块只读接口）

返回所有启用中的对话模型（`status=1、model_type=1`），附带提供商名称：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 5,
      "name": "豆包 Seed 1.6",
      "modelId": "doubao-seed-1-6-250615",
      "modelType": 1,
      "status": 1,
      "providerId": 5,
      "providerName": "火山方舟"
    }
  ]
}
```

---

## 修订记录

| 日期 | 内容 |
|------|------|
| 2026-10-13 | 首版：Agent CRUD + 模型绑定校验 + 知识库/MCP 关联表（关联随 knowledge/mcp 模块落地后开放 UI）；model 模块新增只读下拉接口 `/model-configs/chat-options` |
| 2026-10-14 | Agent 增 `workflowId` 绑定（workflow 模块 `GET /workflows/options` 下拉）；对话页据此分流普通/工作流对话，详见 [chat.md](chat.md) |
