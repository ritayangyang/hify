# 模型提供商 Provider API

管理 LLM 提供商（OpenAI / OpenAI 兼容 / Anthropic / Ollama）的配置、连通性测试与健康状态。

- 模块：`hify-model`
- Base Path：`/api/v1/providers`
- 对应表：`t_provider`、`t_model_config`、`t_provider_health`

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/providers` | 创建提供商 |
| GET | `/providers` | 分页查询提供商 |
| GET | `/providers/{id}` | 查询详情（含模型配置 + 健康状态） |
| PUT | `/providers/{id}` | 更新提供商（部分字段更新） |
| DELETE | `/providers/{id}` | 删除提供商（逻辑删除，级联） |
| POST | `/providers/{id}/test-connection` | 对已保存配置做连通性测试（回写健康状态） |
| POST | `/providers/connection-test` | 表单未保存时直接测试连通性（不落库） |

---

## 数据模型

### ProviderType（提供商类型）

| code | 枚举名 | 协议行为 |
|------|--------|----------|
| 1 | OPENAI | `GET {baseUrl}/v1/models`，Header `Authorization: Bearer {apiKey}` |
| 2 | OPENAI_COMPATIBLE | 同 OPENAI（火山方舟 Ark、DeepSeek、通义等 OpenAI 兼容服务）。baseUrl 已带版本段时直接拼 `/models`，如火山方舟 `https://ark.cn-beijing.volces.com/api/v3` → `/api/v3/models` |
| 3 | ANTHROPIC | `GET {baseUrl}/v1/models`，Header `x-api-key` + `anthropic-version: 2023-06-01` |
| 4 | OLLAMA | `GET {baseUrl}/api/tags`，无认证 |

入参可传 `"OPENAI"` 或 `1`；出参为数字。

### Provider 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 主键 |
| code | string | 提供商编码，唯一（仅活跃记录范围内），用于 fallback 路由 |
| type | int | ProviderType 编码（见上表） |
| name | string | 展示名称 |
| baseUrl | string | API 基础地址 |
| authConfig | object | 认证配置；出参始终脱敏，如 `{"apiKey":"******"}`，无认证为 `{}` |
| status | int | 状态：`1` 启用（默认），`0` 禁用 |
| createdAt | string | 创建时间 ISO-8601 |
| updatedAt | string | 更新时间 ISO-8601 |

### ProviderListItem（列表行）

分页列表每行在 Provider 字段基础上附加健康摘要与模型数（服务端批量装配，单次请求固定 3 条 SQL，无 N+1）：

| 字段 | 类型 | 说明 |
|------|------|------|
| …Provider 全部字段 | — | 同上 |
| failCount | int | 连续失败次数；无健康记录时为 `0` |
| latencyMs | int | 最近一次测试耗时；未测试为 `null` |
| lastSuccessAt | string | 最近一次成功时间；从未成功为 `null` |
| healthStatus | string | 派生健康状态，见下表 |
| enabledModelCount | int | 该提供商下**已启用**（status=1）的模型数量，禁用模型不计 |

健康状态派生规则：

| healthStatus | 条件 | 含义 |
|---|---|---|
| `UP` | failCount=0 且曾成功过 | 最近一次测试成功 |
| `DEGRADED` | failCount>0 且 lastSuccessAt 非空 | 曾经可用，最近连续失败 |
| `DOWN` | failCount>0 且从未成功 | 持续不可用 |
| `UNKNOWN` | failCount=0 且从未测试 | 尚无测试数据 |

### ProviderDetail（详情）

| 字段 | 类型 | 说明 |
|------|------|------|
| provider | Provider | 提供商基础信息 |
| modelConfigs | ModelConfig[] | 该提供商下的模型配置列表 |
| health | ProviderHealth | 健康状态（从未测试也会返回默认值） |

### ModelConfig 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 主键 |
| providerId | long | 所属提供商 |
| name | string | 模型展示名 |
| modelId | string | 真实模型标识（如 `gpt-4o`、`llama3:8b`） |
| modelType | int | `1` 对话模型，`2` 向量模型 |
| status | int | `1` 启用，`0` 禁用 |
| extraParams | object | 附加参数（temperature、context window 等） |

> 模型配置的 CRUD 接口尚未实现，当前仅在详情中返回。

### ProviderHealth 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| providerId | long | 所属提供商 |
| failCount | int | 连续失败次数；测试成功后清零 |
| latencyMs | int | 最近一次测试耗时（毫秒）；未测试为 `null` |
| lastSuccessAt | string | 最近一次成功时间；从未成功为 `null` |

### ConnectionTestResult 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否连通 |
| latencyMs | long | 本次测试耗时（毫秒） |
| modelCount | int | 拉取到的模型数量；失败为 `0` |
| errorMessage | string | 失败原因；成功为 `null` |

---

## 1. 创建提供商

`POST /providers`

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | 否 | 留空时按 type 自动生成（`OPENAI → openai`）；同类型创建多个时必须显式指定 |
| type | string/int | **是** | `"OPENAI"` 或 `1` |
| name | string | **是** | 展示名称，不能为空 |
| baseUrl | string | **是** | API 基础地址，不能为空 |
| authConfig | object | 否 | 如 `{"apiKey":"sk-xxx"}`；OLLAMA 可不传，落库为 `{}` |
| status | int | 否 | 默认 `1` 启用 |

```bash
curl -X POST http://localhost:8080/api/v1/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OpenAI",
    "type": "OPENAI",
    "baseUrl": "https://api.openai.com",
    "authConfig": {"apiKey": "sk-xxx"}
  }'
```

火山方舟（Ark，团队当前使用）示例——baseUrl 必须带 `/api/v3` 版本段：

```bash
curl -X POST http://localhost:8080/api/v1/providers \
  -H "Content-Type: application/json" \
  -d '{
    "code": "ark",
    "name": "火山方舟",
    "type": "OPENAI_COMPATIBLE",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
    "authConfig": {"apiKey": "ark-xxxxxxxx"}
  }'
```

### 成功响应（200）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "code": "openai",
    "type": 1,
    "name": "OpenAI",
    "baseUrl": "https://api.openai.com",
    "authConfig": {"apiKey": "******"},
    "status": 1,
    "createdAt": "2026-10-13T11:23:27.837762",
    "updatedAt": "2026-10-13T11:23:27.837762"
  }
}
```

创建成功后会同时初始化一条健康记录（`failCount=0`）。

### 错误

| 场景 | HTTP / code | message 示例 |
|------|-------------|--------------|
| 缺少 name/baseUrl/type | 400 | `name: name 不能为空` |
| code 与活跃记录重复 | 400 | `提供商编码已存在: openai，同类型创建多个提供商时请显式指定 code` |
| type 值无法识别（如 `"GPT"`） | 400 | `请求体格式错误或参数值非法` |
| 请求体不是合法 JSON | 400 | `请求体格式错误或参数值非法` |

---

## 2. 分页查询提供商

`GET /providers`

### 查询参数

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| page | int | 否 | 1 | 页码，≥1 |
| size / pageSize | int | 否 | 20 | 每页条数，1～50 |
| name | string | 否 | — | 按名称模糊匹配 |
| type | string/int | 否 | — | 按类型筛选：枚举名（`OPENAI`）或数字编码（`1`）；非法值返回 400 |
| status | int | 否 | — | 按状态筛选：`0` 禁用 / `1` 启用 |

```bash
curl "http://localhost:8080/api/v1/providers?page=1&pageSize=10"
```

### 成功响应（200）

records 中每项为 [ProviderListItem](#providerlistitem列表行)：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "code": "openai",
        "type": 1,
        "name": "OpenAI",
        "baseUrl": "https://api.openai.com",
        "authConfig": {"apiKey": "******"},
        "status": 1,
        "createdAt": "2026-10-13T11:23:27.838",
        "updatedAt": "2026-10-13T11:23:27.838",
        "failCount": 0,
        "latencyMs": 320,
        "lastSuccessAt": "2026-10-13T13:09:29.907",
        "healthStatus": "UP",
        "enabledModelCount": 2
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

筛选示例（只看启用的 Ollama 提供商）：

```bash
curl "http://localhost:8080/api/v1/providers?page=1&pageSize=10&type=OLLAMA&status=1"
```

逻辑删除的记录不返回。`size` 超过 50 返回 400：`size: size 最大为 50`；`type` 值无法识别返回 400：`未知的提供商类型: GPT`。

---

## 3. 查询提供商详情

`GET /providers/{id}`

聚合返回提供商基础信息、模型配置列表、健康状态三部分。

```bash
curl http://localhost:8080/api/v1/providers/1
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "provider": {
      "id": 1,
      "code": "openai",
      "type": 1,
      "name": "OpenAI",
      "baseUrl": "https://api.openai.com",
      "authConfig": {"apiKey": "******"},
      "status": 1,
      "createdAt": "2026-10-13T11:23:27.838",
      "updatedAt": "2026-10-13T11:23:27.838"
    },
    "modelConfigs": [],
    "health": {
      "providerId": 1,
      "failCount": 0,
      "latencyMs": null,
      "lastSuccessAt": null
    }
  }
}
```

资源不存在或已删除：`404`，`提供商不存在: {id}`。

---

## 4. 更新提供商

`PUT /providers/{id}`

**部分更新**：请求体中为 `null`/不传的字段保留原值。`code` 与 `createdAt` 不可修改；`updatedAt` 由服务端自动刷新。

### 请求体（全部可选）

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string/int | 修改类型 |
| name | string | 修改名称（传空字符串不生效，保留原值） |
| baseUrl | string | 修改基础地址 |
| authConfig | object | **整体替换**认证配置；不传表示不更新 |
| status | int | `0` 禁用 / `1` 启用 |

```bash
curl -X PUT http://localhost:8080/api/v1/providers/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "OpenAI 生产", "status": 1}'
```

成功响应同[创建](#1-创建提供商)的 `data` 结构。资源不存在返回 `404`。

---

## 5. 删除提供商

`DELETE /providers/{id}`

逻辑删除（`deleted=1`），同一事务内级联：

- `t_model_config`：该提供商下模型配置逻辑删除
- `t_provider_health`：健康记录**物理删除**（下次重新创建同 code 时重新初始化）

删除后该 code 可重新创建使用；重复删除返回 `404`。

```bash
curl -X DELETE http://localhost:8080/api/v1/providers/1
```

```json
{ "code": 200, "message": "success", "data": null }
```

---

## 6. 已保存配置的连通性测试

`POST /providers/{id}/test-connection`

读取库中真实配置（含服务端保存的 apiKey）发起一次 HTTP 探测，并**回写健康状态**：

- 成功：`failCount` 清零，记录 `latencyMs`、`lastSuccessAt`
- 失败：`failCount + 1`，记录本次 `latencyMs`，保留上次 `lastSuccessAt`

HTTP 超时：连接 5s，读取/调用总上限 10s；无论成功失败接口都返回 **200 + ConnectionTestResult**（网络不通是业务结果，不是系统错误）。

```bash
curl -X POST http://localhost:8080/api/v1/providers/1/test-connection
```

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": { "success": true, "latencyMs": 320, "modelCount": 8, "errorMessage": null }
}
```

失败（如网络不可达、401、超时）：

```json
{
  "code": 200,
  "message": "success",
  "data": { "success": false, "latencyMs": 5002, "modelCount": 0, "errorMessage": "连接失败: No route to host" }
}
```

提供商不存在返回 `404`。

---

## 7. 表单直连测试（不落库）

`POST /providers/connection-test`

用于新建/编辑表单里「测试连接」按钮：直接传连接信息测试，**不保存、不回写健康状态**。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string/int | **是** | ProviderType |
| baseUrl | string | **是** | 基础地址 |
| apiKey | string | 否 | API Key；OPENAI/OPENAI_COMPATIBLE/ANTHROPIC 必填，OLLAMA 不传 |

```bash
curl -X POST http://localhost:8080/api/v1/providers/connection-test \
  -H "Content-Type: application/json" \
  -d '{"type": "OLLAMA", "baseUrl": "http://127.0.0.1:11434"}'
```

需要认证的类型（OPENAI / OPENAI_COMPATIBLE / ANTHROPIC）缺少 apiKey 时，接口仍返回 **200**，但业务结果为失败：

```json
{
  "code": 200,
  "message": "success",
  "data": { "success": false, "latencyMs": 0, "modelCount": 0, "errorMessage": "apiKey 不能为空" }
}
```

响应结构同 [ConnectionTestResult](#connectiontestresult-对象)。

---

## 修订记录

| 日期 | 内容 |
|------|------|
| 2026-10-13 | 首版：Provider CRUD + 两类连通性测试；type 入参支持枚举名/数字双格式；code 可缺省自动生成；分页支持 `pageSize` 别名 |
| 2026-10-13 | 分页列表新增 `type` 筛选（配合前端 hify-web/src/api/provider.ts） |
| 2026-10-13 | 分页行升级为 ProviderListItem：新增 `healthStatus`/`failCount`/`latencyMs`/`lastSuccessAt`/`enabledModelCount`；健康状态由服务端派生，健康与模型数批量装配，单次列表请求固定 3 条 SQL，无 N+1 |
| 2026-10-13 | 连通性 URL 拼接修复：baseUrl 已带版本段（`/v1`、`/api/v3` 等）时只拼 `/models`，支持火山方舟 Ark；新增火山方舟创建示例 |
