#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-status}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$ROOT/.run"
LOG_DIR="$ROOT/.logs"

ensure_dirs() {
  mkdir -p "$RUN_DIR" "$LOG_DIR"
}

pid_file() {
  printf '%s/%s.pid' "$RUN_DIR" "$1"
}

log_file() {
  printf '%s/%s.%s.log' "$LOG_DIR" "$1" "$2"
}

stop_pid_file() {
  local name="$1"
  local file
  file="$(pid_file "$name")"
  [[ -f "$file" ]] || return 0

  local pid
  pid="$(tr -d '[:space:]' < "$file")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.25
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -KILL "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    fi
  fi

  rm -f "$file"
}

kill_matching_project_processes() {
  local pattern="$1"
  ps -eo pid=,args= |
    while read -r pid args; do
      [[ -n "${pid:-}" && -n "${args:-}" ]] || continue
      [[ "$pid" != "$$" ]] || continue
      [[ "$args" == *"$ROOT"* && "$args" == *"$pattern"* ]] || continue
      kill -TERM "$pid" 2>/dev/null || true
    done
}

stop_api() {
  stop_pid_file "api"
  kill_matching_project_processes "spring-boot:run"
  kill_matching_project_processes "dev.sg.portfolio.PortfolioApiApplication"
}

stop_web() {
  stop_pid_file "web"
  kill_matching_project_processes "vite"
  kill_matching_project_processes "npm run dev:web"
}

stop_local() {
  stop_pid_file "all"
  stop_api
  stop_web
  kill_matching_project_processes "npm run dev"
}

start_background() {
  local name="$1"
  local workdir="$2"
  local command="$3"
  local out err pidfile
  out="$(log_file "$name" "out")"
  err="$(log_file "$name" "err")"
  pidfile="$(pid_file "$name")"

  ensure_dirs
  if command -v setsid >/dev/null 2>&1; then
    (
      cd "$workdir"
      nohup setsid bash -lc "$command" > "$out" 2> "$err" &
      echo "$!" > "$pidfile"
    )
  else
    (
      cd "$workdir"
      nohup bash -lc "$command" > "$out" 2> "$err" &
      echo "$!" > "$pidfile"
    )
  fi

  echo "$name started: PID $(cat "$pidfile"), logs $out"
}

start_api() {
  stop_api
  start_background "api" "$ROOT/backend" "./mvnw spring-boot:run"
}

start_web() {
  stop_web
  start_background "web" "$ROOT" "npm run dev:web"
}

start_local() {
  start_api
  start_web
  echo "Local URLs:"
  echo "  Frontend: http://localhost:5173"
  echo "  Backend:  http://localhost:8787/api/portfolio/health"
}

show_status() {
  local port
  for port in 5173 8787 5432 5433 8080; do
    if command -v lsof >/dev/null 2>&1; then
      if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "Port $port: listening"
        lsof -nP -iTCP:"$port" -sTCP:LISTEN
      else
        echo "Port $port: free"
      fi
    elif command -v ss >/dev/null 2>&1; then
      if ss -ltnp 2>/dev/null | grep -E "[:.]$port\\b" >/dev/null; then
        echo "Port $port: listening"
        ss -ltnp 2>/dev/null | grep -E "[:.]$port\\b" || true
      else
        echo "Port $port: free"
      fi
    else
      echo "Port $port: status unavailable (install lsof or ss)"
    fi
  done
}

show_env() {
  local env_file="$ROOT/.env"
  if [[ ! -f "$env_file" ]]; then
    echo ".env not found"
    return 0
  fi

  printf '%-45s %-8s %s\n' "Name" "HasValue" "Length"
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" == *"="* ]] || continue
    local name value
    name="${line%%=*}"
    value="${line#*=}"
    name="$(echo "$name" | xargs)"
    if [[ -n "$value" ]]; then
      printf '%-45s %-8s %s\n' "$name" "true" "${#value}"
    else
      printf '%-45s %-8s %s\n' "$name" "false" "0"
    fi
  done < "$env_file"
}

show_log_tail() {
  local name="$1"
  local stream="$2"
  local tail_lines="${3:-80}"
  local file
  file="$(log_file "$name" "$stream")"
  echo
  echo "==> $file"
  if [[ -f "$file" ]]; then
    tail -n "$tail_lines" "$file"
  else
    echo "No existe todavia."
  fi
}

show_local_logs() {
  show_log_tail "api" "out" 60
  show_log_tail "api" "err" 60
  show_log_tail "web" "out" 60
  show_log_tail "web" "err" 60
}

watch_service_logs() {
  local name="$1"
  local out err
  out="$(log_file "$name" "out")"
  err="$(log_file "$name" "err")"
  ensure_dirs
  touch "$out" "$err"
  echo "Siguiendo logs de $name. Ctrl+C para salir."
  tail -n 120 -f "$out" "$err"
}

run_compose() {
  (cd "$ROOT" && docker compose "$@")
}

show_help() {
  cat <<'EOF'
Uso: bash scripts/portfolio.sh <accion>

Acciones locales:
  status        Muestra puertos usados
  env           Lista variables .env sin revelar valores
  up            Levanta backend y frontend local
  down          Detiene backend y frontend local
  restart       Reinicia backend y frontend local
  restart-api   Reinicia solo backend
  restart-web   Reinicia solo frontend
  logs          Muestra ultimas lineas de logs locales
  logs-api      Sigue logs del backend local
  logs-web      Sigue logs del frontend local

Acciones Docker:
  db-up | db-restart
  docker-up | docker-down | docker-restart
  docker-logs | docker-logs-api | docker-logs-web | docker-logs-db
EOF
}

case "$ACTION" in
  help) show_help ;;
  status) show_status ;;
  env) show_env ;;
  up) start_local ;;
  down) stop_local; echo "Local stack stopped." ;;
  restart) stop_local; start_local ;;
  restart-api) start_api ;;
  restart-web) start_web ;;
  logs) show_local_logs ;;
  logs-api) watch_service_logs "api" ;;
  logs-web) watch_service_logs "web" ;;
  db-up) run_compose up -d db ;;
  db-restart) run_compose restart db ;;
  docker-up) run_compose up -d --build ;;
  docker-down) run_compose down ;;
  docker-restart) run_compose restart ;;
  docker-logs) run_compose logs -f ;;
  docker-logs-api) run_compose logs -f backend ;;
  docker-logs-web) run_compose logs -f frontend ;;
  docker-logs-db) run_compose logs -f db ;;
  *)
    echo "Accion no soportada: $ACTION" >&2
    show_help >&2
    exit 1
    ;;
esac
