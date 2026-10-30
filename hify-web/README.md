# Hify Web

Hify 前端：Vue 3 + TypeScript + Vite + Element Plus。

## 目录结构

```
src/
├── api/          # 后端接口定义（按业务拆分）
├── assets/       # 静态资源
├── components/   # 通用组件
├── layouts/      # 布局组件
├── router/       # 路由配置
├── stores/       # Pinia 状态管理
├── styles/       # 全局样式
├── utils/        # 工具（request.ts 为 axios 封装）
├── views/        # 页面
├── App.vue
└── main.ts
```

## 启动

依赖后端 Spring Server（默认 `localhost:8080`），先启动后端：

```bash
cd .. && mvn spring-boot:run -pl hify-bootstrap
```

再启动前端（在本目录）：

```bash
npm install
npm run dev
```

打开 http://localhost:5173 ，默认进入「对话」页（`/chat`）。左侧菜单可切换：
模型管理（`/providers`）、Agent 管理（`/agents`）、对话（`/chat`）。

## Vite 代理

`/api` 请求自动转发到 `http://localhost:8080`（配置见 [vite.config.ts](vite.config.ts)），前端代码直接请求 `/api/v1/xxx` 即可，无跨域问题。

## 构建

```bash
npm run build      # 类型检查 + 产物输出到 dist/
npm run preview    # 本地预览构建产物
```
