# syntax=docker/dockerfile:1
# ============================================================
# Hify 前端镜像：多阶段构建（Node 构建 → Nginx 托管）
# 构建上下文为本目录 hify-web/：
#   docker build -t hify-web:0.1.0 hify-web
# 运行时注入后端地址（同一镜像可对接不同环境）：
#   docker run -p 80:80 -e BACKEND_HOST=10.0.0.5:8080 hify-web:0.1.0
# ============================================================

# ---------- Stage 1: 构建静态产物 ----------
FROM node:22-alpine AS builder
WORKDIR /app

# 先只拷依赖清单，充分利用构建缓存（package.json 未变时不重复 npm ci）
COPY package.json package-lock.json ./
RUN npm ci

# 拷贝源码并构建（package.json 的 build = vue-tsc && vite build，产物在 dist/）
COPY . .
RUN npm run build

# ---------- Stage 2: Nginx 运行时 ----------
FROM nginx:1.27-alpine

# 后端地址：compose 里服务名默认为 hify-backend，K8s/裸机用环境变量覆盖。
# 变量以 NGINX_ 开头，配合 NGINX_ENVSUBST_FILTER 只替换它，
# 避免模板里的 nginx 内置变量（$host/$uri/$remote_addr 等）被 envsubst 清空。
ENV NGINX_BACKEND_HOST=hify-backend:8080
ENV NGINX_ENVSUBST_FILTER=NGINX_

# 官方 nginx 镜像启动时自动 envsubst /etc/nginx/templates/*.template
# 输出到 /etc/nginx/conf.d/default.conf（见镜像 20-envsubst-on-templates.sh）
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

# 容器探活：nginx 能返回首页即健康（busybox wget）
HEALTHCHECK --interval=10s --timeout=3s --retries=3 --start-period=5s \
    CMD wget -qO /dev/null http://127.0.0.1/ || exit 1
