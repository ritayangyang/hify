#!/usr/bin/env bash
# ============================================================
# Hify 本地部署启动脚本
#
# 配置注入优先级：已导出的环境变量 > conf/hify.env 配置文件 > application.yml 中的默认值。
# 用法：
#   bin/start.sh              前台检查后后台启动，并探活
#   HIFY_SERVER_PORT=8090 bin/start.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR_FILE="$APP_HOME/app/hify-backend.jar"
CONF_DIR="$APP_HOME/conf"
ENV_FILE="$CONF_DIR/hify.env"
PID_FILE="$APP_HOME/run/hify.pid"
LOG_DIR="$APP_HOME/logs"
LOG_FILE="$LOG_DIR/hify.out"

STARTUP_TIMEOUT_SECONDS=60

mkdir -p "$APP_HOME/run" "$LOG_DIR"

# ------------------------------------------------------------
# 1. 加载配置文件（不覆盖已导出的环境变量）
# ------------------------------------------------------------
if [ -f "$ENV_FILE" ]; then
    while IFS='=' read -r key value || [ -n "$key" ]; do
        case "$key" in
            ''|\#*) continue ;;  # 空行与注释
        esac
        key="$(echo "$key" | tr -d '[:space:]')"
        # 去掉值两端空白与成对引号
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%[[:space:]]}"
        value="${value%\"}"; value="${value#\"}"
        value="${value%\'}"; value="${value#\'}"
        if [ -z "${!key:-}" ]; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
fi

# ------------------------------------------------------------
# 2. 默认值（application.yml 中也有同值默认，此处供探活使用）
# ------------------------------------------------------------
export HIFY_SERVER_PORT="${HIFY_SERVER_PORT:-8080}"
# 前端静态目录默认用包内 dist 的绝对路径，避免随工作目录变化
export HIFY_WEB_DIR="${HIFY_WEB_DIR:-$APP_HOME/dist}"

# ------------------------------------------------------------
# 3. 前置检查
# ------------------------------------------------------------
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] 未找到 java 命令，目标机器需预装 JDK 21+。" >&2
    exit 1
fi
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)"
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 21 ]; then
    echo "[ERROR] Hify 需要 JDK 21+，当前 java 主版本：${JAVA_MAJOR:-未知}" >&2
    exit 1
fi
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERROR] 后端 jar 不存在：$JAR_FILE" >&2
    exit 1
fi
if [ -f "$PID_FILE" ]; then
    EXISTING_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [ -n "$EXISTING_PID" ] && kill -0 "$EXISTING_PID" 2>/dev/null; then
        echo "[ERROR] Hify 已在运行，PID=${EXISTING_PID}，如需重启请先执行 bin/stop.sh" >&2
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# ------------------------------------------------------------
# 4. 后台启动（JVM 参数遵循 CLAUDE.md 部署规范，可用 JAVA_OPTS_EXTRA 追加）
# ------------------------------------------------------------
DEFAULT_JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"
# shellcheck disable=SC2086 # JAVA_OPTS 需要按空格拆分
JAVA_OPTS="${JAVA_OPTS:-$DEFAULT_JAVA_OPTS} ${JAVA_OPTS_EXTRA:-}"

echo "[INFO] APP_HOME=$APP_HOME"
echo "[INFO] 端口=$HIFY_SERVER_PORT  MySQL=${HIFY_MYSQL_HOST:-127.0.0.1}:${HIFY_MYSQL_PORT:-3306}/${HIFY_MYSQL_DATABASE:-hify}  日志=$LOG_FILE"

nohup java $JAVA_OPTS \
    -jar "$JAR_FILE" \
    "--spring.config.additional-location=file:$CONF_DIR/" \
    > "$LOG_FILE" 2>&1 &
APP_PID=$!
echo "$APP_PID" > "$PID_FILE"

# ------------------------------------------------------------
# 5. 等待端口就绪（纯 bash /dev/tcp，不依赖 curl）
# ------------------------------------------------------------
port_open() {
    local host="$1" port="$2"
    (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1 && { exec 3>&-; exec 3<&-; return 0; }
    return 1
}

for ((i = 1; i <= STARTUP_TIMEOUT_SECONDS; i++)); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "[ERROR] 进程已退出，末尾日志：" >&2
        tail -30 "$LOG_FILE" >&2 || true
        rm -f "$PID_FILE"
        exit 1
    fi
    if port_open 127.0.0.1 "$HIFY_SERVER_PORT"; then
        echo "[OK] Hify 启动成功（PID=${APP_PID}，耗时约 ${i}s）"
        echo "     健康检查: curl http://localhost:$HIFY_SERVER_PORT/api/v1/health"
        echo "     访问入口: http://localhost:$HIFY_SERVER_PORT/"
        exit 0
    fi
    sleep 1
done

echo "[WARN] ${STARTUP_TIMEOUT_SECONDS}s 内端口未就绪，进程仍在运行（PID=${APP_PID}），请查看日志：${LOG_FILE}" >&2
exit 1
