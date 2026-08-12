#!/usr/bin/env bash
# AICP 本地一键启动（dev）
# 用法：
#   ./scripts/dev-up.sh          # 启动全部
#   ./scripts/dev-up.sh stop     # 停止由本脚本拉起的进程
#   ./scripts/dev-up.sh status   # 查看端口状态
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT}/.dev-logs"
PID_DIR="${ROOT}/.dev-pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

export PATH="${HOME}/.bun/bin:${PATH}"

# 与 aicp-backend application.yml (dev) 对齐，便于 SSO / 服务鉴权
export AICP_JWT_SECRET="${AICP_JWT_SECRET:-aicp-jwt-secret-key-2026-very-long-secret-key-for-hs256}"
export AICP_SERVICE_SECRET="${AICP_SERVICE_SECRET:-dev-wallet-secret}"
export SESSION_SECRET="${SESSION_SECRET:-local-dev-session-secret}"
export CRYPTO_SECRET="${CRYPTO_SECRET:-local-dev-crypto-secret}"
export REDIS_CONN_STRING="${REDIS_CONN_STRING:-redis://127.0.0.1:6379/1}"
export SQLITE_PATH="${SQLITE_PATH:-${ROOT}/one-api.db}"
export PORT="${NEW_API_PORT:-3000}"
export TZ="${TZ:-Asia/Shanghai}"
export VITE_REACT_APP_SERVER_URL="${VITE_REACT_APP_SERVER_URL:-http://localhost:3000}"
export VITE_AICP_WORKBENCH_URL="${VITE_AICP_WORKBENCH_URL:-http://localhost:5173}"

port_busy() {
  local port="$1"
  ss -tln 2>/dev/null | grep -qE ":${port}\\s" || return 1
}

ensure_redis() {
  if redis-cli ping >/dev/null 2>&1; then
    echo "[ok] Redis already up"
    return
  fi
  if command -v redis-server >/dev/null 2>&1; then
    echo "[..] starting redis-server on 6379"
    redis-server --daemonize yes --port 6379 --bind 127.0.0.1
    sleep 0.5
    redis-cli ping >/dev/null
    echo "[ok] Redis started"
    return
  fi
  echo "[err] Redis not available. Install redis-server or start Redis on 6379." >&2
  exit 1
}

ensure_embed_placeholders() {
  local d1="${ROOT}/new-api/web/default/dist"
  local d2="${ROOT}/new-api/web/classic/dist"
  mkdir -p "$d1" "$d2"
  if [[ ! -f "${d1}/index.html" ]]; then
    printf '%s\n' '<!doctype html><html><head><title>dev</title></head><body>use frontend dev server</body></html>' >"${d1}/index.html"
  fi
  if [[ ! -f "${d2}/index.html" ]]; then
    cp "${d1}/index.html" "${d2}/index.html"
  fi
}

start_bg() {
  local name="$1"
  shift
  local log="${LOG_DIR}/${name}.log"
  local pidf="${PID_DIR}/${name}.pid"
  if [[ -f "$pidf" ]] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
    echo "[ok] ${name} already running (pid $(cat "$pidf"))"
    return
  fi
  echo "[..] starting ${name} → ${log}"
  (
    cd "$ROOT"
    nohup "$@" >"$log" 2>&1 &
    echo $! >"$pidf"
  )
}

wait_http() {
  local url="$1"
  local label="$2"
  local n=0
  while (( n < 90 )); do
    if curl -sf -o /dev/null "$url"; then
      echo "[ok] ${label} ready  ${url}"
      return 0
    fi
    sleep 2
    n=$((n + 1))
  done
  echo "[warn] ${label} not ready yet: ${url} (see ${LOG_DIR}/)"
  return 1
}

cmd_stop() {
  echo "Stopping managed processes..."
  for f in "${PID_DIR}"/*.pid; do
    [[ -f "$f" ]] || continue
    local name pid
    name="$(basename "$f" .pid)"
    pid="$(cat "$f" 2>/dev/null || true)"
    if [[ -n "${pid}" ]] && kill -0 "$pid" 2>/dev/null; then
      # kill process group children when possible
      kill "$pid" 2>/dev/null || true
      # also kill common child trees by port owners if still alive later
      echo "[ok] stopped ${name} (pid ${pid})"
    fi
    rm -f "$f"
  done
  # best-effort: stop go/java/node children that may have re-parented
  pkill -f 'aicp-backend.*spring-boot:run' 2>/dev/null || true
  pkill -f 'new-api.*go run' 2>/dev/null || true
  # do not kill redis — shared service
  echo "Done. Redis left running."
}

cmd_status() {
  printf '%-22s %s\n' "Service" "Port/URL"
  for item in \
    "Redis|6379|redis://127.0.0.1:6379" \
    "AICP backend|8080|http://localhost:8080" \
    "AICP frontend|5173|http://localhost:5173" \
    "new-api API|3000|http://localhost:3000" \
    "new-api web|3001|http://localhost:3001"
  do
    IFS='|' read -r name port url <<<"$item"
    if port_busy "$port"; then
      printf '%-22s UP  %s\n' "$name" "$url"
    else
      printf '%-22s DOWN %s\n' "$name" "$url"
    fi
  done
}

cmd_up() {
  ensure_redis
  ensure_embed_placeholders

  if ! port_busy 8080; then
    start_bg aicp-backend bash -lc "cd '${ROOT}/aicp-backend' && mvn -q spring-boot:run -Dspring-boot.run.profiles=dev"
  else
    echo "[ok] :8080 already in use — skip aicp-backend"
  fi

  if ! port_busy 5173; then
    start_bg aicp-frontend bash -lc "cd '${ROOT}/aicp-frontend' && npm run dev -- --host 0.0.0.0 --port 5173"
  else
    echo "[ok] :5173 already in use — skip aicp-frontend"
  fi

  if ! port_busy 3000; then
    start_bg new-api-api bash -lc "cd '${ROOT}/new-api' && exec go run ."
  else
    echo "[ok] :3000 already in use — skip new-api"
  fi

  if ! port_busy 3001; then
    start_bg new-api-web bash -lc "cd '${ROOT}/new-api/web/default' && exec bun run dev"
  else
    echo "[ok] :3001 already in use — skip new-api web"
  fi

  echo
  echo "Waiting for health..."
  wait_http "http://127.0.0.1:8080/api/health" "AICP backend" || true
  wait_http "http://127.0.0.1:5173/" "AICP frontend" || true
  wait_http "http://127.0.0.1:3000/api/status" "new-api API" || true
  wait_http "http://127.0.0.1:3001/" "new-api web" || true

  echo
  echo "=============================="
  echo "  AICP workbench  http://localhost:5173"
  echo "  AICP API        http://localhost:8080"
  echo "  Model console   http://localhost:3001  (API → :3000)"
  echo "  Logs            ${LOG_DIR}/"
  echo "  Stop            ./scripts/dev-up.sh stop"
  echo "=============================="
  echo "First-time new-api: open http://localhost:3001/setup to create admin."
  echo "AICP dev account: POST /api/v1/auth/dev/init  {\"account\":\"admin\",\"password\":\"admin123\"}"
}

case "${1:-up}" in
  up|start) cmd_up ;;
  stop) cmd_stop ;;
  status) cmd_status ;;
  *)
    echo "Usage: $0 [up|stop|status]" >&2
    exit 1
    ;;
esac
