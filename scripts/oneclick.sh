#!/bin/bash
set -euo pipefail

# Usage: ./scripts/oneclick.sh [dev|prod] [--attach]
ENVIRONMENT=${1:-dev}
ATTACH_FLAG=${2:-}
ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

printf "[oneclick] Environment: %s\n" "$ENVIRONMENT"
echo "[oneclick] Services will be started in background; use --attach to stream logs here."

# 1) Ensure MySQL is up and schema exists via setup.sh (idempotent check inside)
"$ROOT_DIR/scripts/setup.sh" || {
  echo "[oneclick] setup.sh failed. Please check MySQL/container logs." >&2
  exit 1
}

# 2) Start backend (cargo run) in background
if pgrep -f "cryptopedia" >/dev/null 2>&1; then
  echo "[oneclick] Backend appears to be already running (process matched). Skipping start."
else
  (
    cd "$ROOT_DIR"
    ENVIRONMENT=$ENVIRONMENT RUST_LOG=${RUST_LOG:-info} \
      cargo run >"$LOG_DIR/backend.log" 2>&1 &
    echo $! > "$LOG_DIR/backend.pid"
  )
  echo "[oneclick] Backend starting... logs: $LOG_DIR/backend.log"
fi

# 3) Start frontend (Vite+React) if present
FRONT_DIR="$ROOT_DIR/frontend"
if [ -d "$FRONT_DIR" ] && [ -f "$FRONT_DIR/package.json" ]; then
  # install deps if node_modules missing
  if [ ! -d "$FRONT_DIR/node_modules" ]; then
    (
      cd "$FRONT_DIR"
      if command -v pnpm >/dev/null 2>&1; then pnpm install; 
      elif command -v yarn >/dev/null 2>&1; then yarn install; 
      else npm install; fi
    )
  fi

  if pgrep -f "vite" >/dev/null 2>&1; then
    echo "[oneclick] Frontend appears to be already running (vite). Skipping start."
  else
    (
      cd "$FRONT_DIR"
      export VITE_API_BASE_URL=${VITE_API_BASE_URL:-http://127.0.0.1:8080}
      # run dev server on all interfaces, default Vite port 5173
      if command -v pnpm >/dev/null 2>&1; then pnpm run dev -- --host 0.0.0.0 >"$LOG_DIR/frontend.log" 2>&1 &
      elif command -v yarn >/dev/null 2>&1; then yarn dev --host 0.0.0.0 >"$LOG_DIR/frontend.log" 2>&1 &
      else npm run dev -- --host 0.0.0.0 >"$LOG_DIR/frontend.log" 2>&1 & fi
      echo $! > "$LOG_DIR/frontend.pid"
    )
    echo "[oneclick] Frontend starting... logs: $LOG_DIR/frontend.log"
  fi
else
  echo "[oneclick] Frontend folder not found. Skipping (expected at $FRONT_DIR)."
fi

# 4) Print helpful info
cat <<INFO
[oneclick] Done.
- Backend: http://127.0.0.1:${SERVER_PORT:-8080}
- API:     http://127.0.0.1:${SERVER_PORT:-8080}/api/v1/arbitrage/BTC?from=Binance&to=Upbit&fx=usdtkrw&fees=include
- Frontend (if present): http://127.0.0.1:5173

Logs:
- Backend:  $LOG_DIR/backend.log (pid: $(cat "$LOG_DIR/backend.pid" 2>/dev/null || echo "-"))
- Frontend: $LOG_DIR/frontend.log (pid: $(cat "$LOG_DIR/frontend.pid" 2>/dev/null || echo "-"))
INFO

# Optional: attach to both logs
if [ "$ATTACH_FLAG" = "--attach" ] || [ "${ATTACH_LOGS:-0}" = "1" ]; then
  echo "[oneclick] Attaching to logs (Ctrl+C to detach)..."
  trap 'trap - INT; kill 0' INT TERM
  # tail with prefixes (fallback if stdbuf is unavailable)
  TAIL_CMD="tail -n 100 -F"
  if command -v stdbuf >/dev/null 2>&1; then
    TAIL_CMD="stdbuf -oL $TAIL_CMD"
  fi
  (
    eval "$TAIL_CMD \"$LOG_DIR/backend.log\"" | sed -u 's/^/[backend] /'
  ) &
  (
    eval "$TAIL_CMD \"$LOG_DIR/frontend.log\"" | sed -u 's/^/[frontend] /'
  ) &
  wait
fi
