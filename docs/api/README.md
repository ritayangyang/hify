# Hify API 文档

Hify 后端 HTTP 接口文档。当前已实现模块：

| 文档 | 模块 |
|------|------|
| [模型提供商 Provider](model-provider.md) | model（hify-model） |
| [Agent 管理](agent.md) | agent（hify-agent），含模型下拉只读接口 |
| [工作流管理](workflow.md) | workflow（hify-workflow），节点/连线三表整体维护 |

> 对话、知识库、MCP 模块接口实现后在本表追加。

---

## 1. 通用约定

### Base URL

```
http://localhost:8080/api/v1
```

- 所有业务接口前缀 `/api/v1`
- 请求/响应统一使用 `application/json; charset=UTF-8`
- 前端开发环境通过 Vite 代理 `/api` → `localhost:8080`

### 统一响应包装

所有接口返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码，与 HTTP 状态码一致；`200` 成功 |
| message | string | 成功为 `success`；失败为具体错误信息（参数校验会精确到字段） |
| data | any | 业务数据；无数据时为 `null` |

### 错误码

HTTP 状态码与响应体 `code` 保持一致：

| code | 枚举 | 触发场景 |
|------|------|----------|
| 400 | PARAM_ERROR | 参数校验失败、业务规则不满足（如编码重复）、请求体 JSON 非法/枚举值无法识别 |
| 401 | UNAUTHORIZED | 未认证（预留） |
| 403 | FORBIDDEN | 无权限（预留） |
| 404 | NOT_FOUND | 资源不存在 |
| 500 | INTERNAL_ERROR | 未预期的系统异常，堆栈只记日志，不返回前端 |

业务错误示例：

```json
{ "code": 400, "message": "提供商编码已存在: openai", "data": null }
```

字段校验错误（多个字段用 `; ` 拼接）：

```json
{ "code": 400, "message": "name: name 不能为空; baseUrl: baseUrl 不能为空", "data": null }
```

### 分页约定

查询参数：

| 参数 | 类型 | 默认 | 约束 |
|------|------|------|------|
| page | int | 1 | ≥ 1 |
| size / pageSize | int | 20 | 1～50（`pageSize` 为别名，二者等价） |
| 其它筛选参数 | — | — | 见各接口 |

分页响应 `data` 为 `PageResponse<T>`：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 20
}
```

### 枚举传参

枚举类型**入参同时接受两种形式**（大小写不敏感），**出参一律为数字编码**：

- 枚举名字符串：`"OPENAI"`
- 数字（字符串或数字均可）：`1`

无法识别的枚举值返回 `400 PARAM_ERROR`（请求体格式错误或参数值非法）。

### 时间格式

`LocalDateTime` 序列化为 ISO-8601：`2026-10-13T11:23:27.837762`。

### 密钥安全

`authConfig.apiKey` 等密钥**永远不会通过接口明文返回**：

- 已配置密钥时出参固定脱敏为 `"******"`
- 未配置时 `authConfig` 为 `{}`（无认证类型，如 OLLAMA）
- 真实密钥仅服务端内部使用（如连通性测试），并禁止打印到日志

---

## 2. 健康检查

### `GET /health`

服务探活，无需认证。

```bash
curl http://localhost:8080/api/v1/health
```

```json
{ "code": 200, "message": "success", "data": "Hify is running" }
```

> 另有一个轻量探活端点 `GET /health`（无 `/api/v1` 前缀）返回纯文本 `success`，供基础设施探测使用。
