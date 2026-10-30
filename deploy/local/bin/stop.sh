#!/usr/bin/env bash
# ============================================================
# Hify 本地部署停止脚本：SIGTERM 优雅停止（配合 Spring graceful shutdown），
# 等待进程退出；超时（默认 35s，覆盖 30s 优雅期）后 SIGKILL 兜底。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

PID_FILE="$APP_HOME/run/hify.pid"
GRACE_TIMEOUT_SECONDS=35

if [ ! -f "$PID_FILE" ]; then
    echo "[INFO] 未找到 PID 文件，Hify 似乎未在运行：$PID_FILE"
    exit 0
fi

APP_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [ -z "$APP_PID" ] || ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "[INFO] 进程已不存在，清理过期 PID 文件。"
    rm -f "$PID_FILE"
    exit 0
fi

echo "[INFO] 发送 SIGTERM，等待 Hify 优雅停止（PID=${APP_PID}）..."
kill -TERM "$APP_PID" 2>/dev/null || true

for ((i = 1; i <= GRACE_TIMEOUT_SECONDS; i++)); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        rm -f "$PID_FILE"
        echo "[OK] Hify 已停止（耗时约 ${i}s）。"
        exit 0
    fi
    sleep 1
done

echo "[WARN] ${GRACE_TIMEOUT_SECONDS}s 内未退出，发送 SIGKILL 强制结束。" >&2
kill -KILL "$APP_PID" 2>/dev/null || true
# 等待僵尸回收，确认死亡
for ((i = 1; i <= 5; i++)); do
    kill -0 "$APP_PID" 2>/dev/null || break
    sleep 1
done
rm -f "$PID_FILE"
echo "[OK] Hify 已强制停止。"
