# 对话 API

多轮对话与会话管理：基于 Agent 发起会话、SSE 流式收发消息、游标分页查询历史。

- 模块：`hify-conversation`
- Base Path：`/api/v1/chat`
- 对应表：`t_chat_session`、`t_chat_message`

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/sessions` | 创建会话（绑定一个启用中的 Agent） |
| GET | `/chat/sessions` | 游标分页查询会话列表 |
| DELETE | `/chat/sessions/{id}` | 删除会话（消息同事务逻辑删除） |
| GET | `/chat/sessions/{id}/messages` | 游标分页查询消息历史 |
| POST | `/chat/sessions/{id}/messages` | 发送消息，返回 **SSE 流** |

---

## 数据模型

### ChatSession

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 主键 |
| agentId | long | 会话绑定的 Agent（创建时固定） |
| title | string | 标题，默认「新会话」；**首条消息发送后**自动取消息正文前 20 字 |
| status | string | 固定 `ACTIVE`（预留字段） |
| createdAt | string | ISO-8601 |
| updatedAt | string | ISO-8601，新消息落库时刷新 |

### ChatMessage

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 主键 |
| sessionId | long | 所属会话 |
| role | string | `user` / `assistant` |
| content | string | 消息正文（MEDIUMTEXT） |
| tokens | int \| null | assistant 消息的 token 用量；user 消息为 null |
| finishReason | string \| null | 结束原因，成功为 `stop`；user 消息为 null |
| latencyMs | int \| null | 下游耗时毫秒；user 消息为 null |
| createdAt | string | ISO-8601 |

### CursorPage（游标分页）

所有列表接口统一形态，**不使用 offset 深分页**：

```json
{ "records": [ ... ], "nextCursor": 21 }
```

- 按 `id DESC` 返回（最新在前），`nextCursor` 为当前页最后一条的 id；
- `nextCursor = null` 表示没有更多；取下一页时把它作为 `cursorId` 传回；
- 前端展示消息时自行反转为时间升序。

---

## 1. 创建会话

`POST /chat/sessions`

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | long | **是** | 必须存在且为启用状态（`status=1`） |

```bash
curl -X POST http://localhost:8080/api/v1/chat/sessions \
  -H "Content-Type: application/json" \
  -d '{"agentId": 4}'
```

### 错误

| 场景 | HTTP | message 示例 |
|------|------|--------------|
| 缺 agentId / 为空 | 400 | `agentId 不能为空` |
| Agent 已停用 | 400 | `Agent 已停用: 客服退货agent` |
| Agent 不存在 | 404 | `Agent 不存在: 999` |

## 2. 查询会话列表

`GET /chat/sessions`

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| cursorId | long | — | 游标，不传取第一页（最新一页） |
| size | int | 20 | 1～100，超限自动截断 |

```bash
curl "http://localhost:8080/api/v1/chat/sessions?size=20"
```

## 3. 删除会话

`DELETE /chat/sessions/{id}`

逻辑删除会话，消息在**同一事务**内逻辑删除。重复删除 / 会话不存在返回 `404`（`会话不存在: {id}`）。

## 4. 查询消息历史

`GET /chat/sessions/{id}/messages?cursorId=&size=`

- 参数同分页规范；返回 `id DESC`，最新消息在前；
- 会话不存在返回 `404`。

```bash
curl "http://localhost:8080/api/v1/chat/sessions/1/messages?size=20"
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 2, "sessionId": 1, "role": "user",
        "content": "你好，请简短回复",
        "tokens": null, "finishReason": null, "latencyMs": null,
        "createdAt": "2026-10-14T13:41:10.816"
      }
    ],
    "nextCursor": null
  }
}
```

## 5. 发送消息（SSE 流式）

`POST /chat/sessions/{id}/messages`，响应 `Content-Type: text/event-stream`。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | **是** | 非空白，最长受消息表 MEDIUMTEXT 约束 |

```bash
curl -N -X POST http://localhost:8080/api/v1/chat/sessions/1/messages \
  -H "Content-Type: application/json" \
  -d '{"content":"我要退货"}'
```

HTTP 层错误（会话不存在、content 为空）在建流前以普通 `Result` JSON 返回：
`404 会话不存在: {id}` / `400 content: content 不能为空`。

### SSE 事件协议

未命名事件，每帧 `data:` 后接一个 JSON，事件类型由 JSON 内 `type` 区分：

| type | 字段 | 时机 |
|------|------|------|
| `delta` | `content` | 流式增量；普通 LLM 分支多次到达，工作流分支**整段到达一次** |
| `done` | `finishReason`（固定 `stop`）、`latencyMs` | 成功结束 |
| `error` | `content`（给用户看的中文提示） | 下游失败；帧后流即结束，**不会**再有 done |

```
data:{"type":"delta","content":"你好"}

data:{"type":"delta","content":"，有什么可以帮你？"}

data:{"type":"done","finishReason":"stop","latencyMs":3200}
```

失败示例（如下游模型未开通）：

```
data:{"type":"error","content":"LLM 调用失败(HTTP 404): The model or endpoint ..."}
```

### 两条执行分支

加载会话绑定的 Agent 后按 `agent.workflowId` 分流：

1. **普通对话分支**（`workflowId = null`）：`systemPrompt`（非空才加）+ 最近 20 条历史（id DESC 取近后内存反转为时间升序）+ 当前消息，走 LLM 流式接口逐段转发 `delta`。
2. **工作流分支**（`workflowId != null`，教程 26 章能力）：同步执行绑定的工作流，用户消息固定映射为入参 **`inputs.userMessage`**，经 workflow 模块的 `WorkflowRunService` api 调用。工作流非流式，成功后把完整输出作为**一个** delta 整段下发，`latencyMs` 取工作流运行耗时，`tokens` 取工作流累计 token。

### 落库与失败语义

- **user 消息在调用下游前先落库**，下游失败也不回滚（保留用户输入）；首条消息同时更新标题。
- **assistant 消息只在成功路径落库**：失败只推 `error` 事件，不产生孤儿 assistant 消息。
- 工作流异常（含变量不存在、节点执行失败等 `BizException`）消息原样透传在 `error.content`；未预期异常统一为「对话处理失败，请稍后再试」并记录 ERROR 日志。
- 会话不存在 / Agent 不存在或已停用同样以 `error` 事件返回（此时无 user 消息落库）。
- 客户端主动断开（abort）：静默结束，不推 error、不打 ERROR，已生成的片段不入库。
- SSE 整体超时 200s；流式线程池（30/80，队列 50）满载时请求线程直接返回
  `{"type":"error","content":"服务器繁忙，请稍后再试"}`。

前端注意：该接口为 POST + 长连接，不要用 EventSource（仅支持 GET），也不要走带 15s 超时的 axios 实例；使用原生 `fetch` + ReadableStream 读取，按空行切帧。

---

## 修订记录

| 日期 | 内容 |
|------|------|
| 2026-10-14 | 首版：会话/消息五接口、SSE 协议、普通 LLM 与工作流两条分支；上下文暂走 MySQL（最近 20 条），`ChatContextStore` 端口预留 Redis 实现 |
