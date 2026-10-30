# Hify 本地部署说明

面向几人到几十人的内网使用。目标机器只需 **JDK 21+**，数据库为外部服务，本包不含 MySQL / Redis / pgvector。

## 1. 包内容

```
hify/
├── app/hify-backend.jar   # 后端可执行 fat jar（同时托管前端页面）
├── dist/                  # 前端构建产物
├── conf/
│   ├── application.yml    # 配置模板（环境变量占位，已含默认值）
│   └── hify.env.example   # 环境变量示例（复制为 hify.env 后填写）
├── bin/start.sh / stop.sh # 启动 / 优雅停止
├── logs/                  # 运行日志（hify.out）
└── run/hify.pid           # 进程 PID 文件
```

## 2. 前置准备

1. JDK 21+（`java -version` 确认）；Linux/macOS，bash。
2. 一个可连通的 **MySQL 8.x**：
   - 建库：`CREATE DATABASE hify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
   - 导入建表脚本：仓库各模块 `hify-*/src/main/resources/db/schema.sql`（均幂等）。
   - 建账号并只授予业务库权限。
3. **Redis / pgvector 本期可不部署**：对话上下文走 MySQL；pgvector 要等知识库模块上线。未部署时保持默认地址即可，应用启动不依赖它们（惰性连接）。
4. LLM 出网：服务器需能访问火山方舟等外部模型 API；如需代理，在 `hify.env` 中用 `JAVA_OPTS_EXTRA` 追加 `-Dhttps.proxyHost=... -Dhttps.proxyPort=...`。

## 3. 首次部署

```bash
tar xzf hify-<version>.tar.gz
cd hify
cp conf/hify.env.example conf/hify.env
vi conf/hify.env        # 填 MySQL 连接信息（其余保持默认）
bin/start.sh
```

启动脚本会探活，成功后：

- 健康检查：`curl http://localhost:8080/api/v1/health` → `{"code":200,...,"data":"ok"}`
- 浏览器访问：`http://<服务器IP>:8080/`（前端由后端同源托管，history 路由已支持）

## 4. 配置注入规则

优先级：**已 export 的环境变量 > `conf/hify.env` > `conf/application.yml` 内默认值**。

可用变量（完整清单见 `conf/hify.env.example`）：

| 变量 | 默认 | 说明 |
|---|---|---|
| HIFY_SERVER_PORT | 8080 | 服务端口 |
| HIFY_MYSQL_HOST/PORT/DATABASE/USER/PASSWORD | 127.0.0.1/3306/hify/hify/hify123 | MySQL 连接 |
| HIFY_REDIS_* | 本机 6379 | Redis（预留，可不部署） |
| HIFY_PGVECTOR_* | 本机 5432 | pgvector（知识库上线前不使用） |
| HIFY_WEB_DIR | 包内 dist | 前端目录 |
| JAVA_OPTS_EXTRA | 空 | 追加 JVM 参数（内存、代理等） |

临时改端口示例：`HIFY_SERVER_PORT=8090 bin/start.sh`

## 5. 停止与升级

```bash
bin/stop.sh             # SIGTERM 优雅停止，最多等 35s，仍未退出则 SIGKILL
```

升级：`bin/stop.sh` → 解压新包（保留旧包的 `conf/hify.env`）→ 检查 schema.sql 是否有新表 → `bin/start.sh`。

## 6. 日志与 traceId

日志全部输出到 stdout（tar 包重定向在 `logs/hify.out`），两种形态由 Spring profile 决定：

| profile | 格式 | 适用 |
|---|---|---|
| 默认（不设） | 可读文本，行内带 `[traceId=...,spanId=...]` | 本地/tar 部署，人工排查 |
| `prod`（`SPRING_PROFILES_ACTIVE=prod`） | JSON 单行：`timestamp/level/traceId/spanId/thread/logger/message[/stack_trace]` | K8s/Docker，由日志 Agent 采集 |

- traceId 由 OpenTelemetry（micrometer-tracing 桥接）按请求生成，一期不导出到 Jaeger/Zipkin；
  同一次对话在 Tomcat 线程、`llm-stream`、`llm-pool`（工作流内 LLM 调用）上的日志共享同一个 traceId，
  用它即可在日志系统里过滤出完整链路。
- 关键节点：HTTP 请求进入/完成、对话开始与分支（normal/workflow）、LLM 调用开始/成功/失败、
  工作流调用开始/成功、未预期异常（带 `stack_trace`）。
- `/api/v1/health` 与 `/actuator` 不打访问日志，避免探针噪音。
- K8s 部署时在容器环境变量设置 `SPRING_PROFILES_ACTIVE=prod`；将来接入链路追踪平台时，
  加 OTLP exporter 依赖即可，现有埋点无需改动。

## 7. 常见问题

- 启动失败：先看 `logs/hify.out` 末尾日志；最常见是 MySQL 不通或库表未初始化。
- 8080 被占用：改 `hify.env` 的 `HIFY_SERVER_PORT`。
- API Key：LLM 提供商密钥在页面里录入（库存明文、接口脱敏），不进入本配置文件。
- 无鉴权说明：本系统没有登录体系，只能部署在可信内网，边界由防火墙/反向代理保证。
