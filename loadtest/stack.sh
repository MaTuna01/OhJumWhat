#!/usr/bin/env bash
# 로컬 복제 스택(Caddy → 앱 → DB, 운영 compose + 한도 오버라이드)을 다루는 명령 모음.
#   loadtest/stack.sh build            운영 Dockerfile로 ohjumwhat:loadtest 이미지를 만든다
#   loadtest/stack.sh up               스택을 띄우고 /healthz UP과 pg_stat_statements 확장을 확인한다
#   loadtest/stack.sh seed 15 20 [시드 인자…]   예: seed 50 20 --ohjumwhat.loadtest.history=500 --ohjumwhat.loadtest.closes-in=PT6M
#   loadtest/stack.sh clean
#   loadtest/stack.sh psql [-c "…"]    db 컨테이너의 psql
#   loadtest/stack.sh logs [서비스]
#   loadtest/stack.sh restart-app      재연결 폭풍 시나리오 앞에서 앱만 다시 띄운다
#   loadtest/stack.sh down [-v]
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PROJECT=ohjumwhat-loadtest
export LOADTEST_ENV_FILE="$ROOT/loadtest/.env.local"
OUT_DIR="$ROOT/loadtest/out"

if [[ ! -f "$LOADTEST_ENV_FILE" ]]; then
  echo "loadtest/.env.local 이 없습니다. loadtest/.env.local.example 을 복사해 채워 주세요." >&2
  exit 1
fi

compose() {
  docker compose -p "$PROJECT" --env-file "$LOADTEST_ENV_FILE" \
    -f "$ROOT/deploy/docker-compose.yml" -f "$ROOT/loadtest/docker-compose.limits.yml" "$@"
}

db_env() { grep -E "^$1=" "$LOADTEST_ENV_FILE" | cut -d= -f2-; }

wait_healthy() {
  echo "앱이 뜨기를 기다립니다(https://localhost/healthz)…"
  for _ in $(seq 1 90); do
    if curl -sk https://localhost/healthz 2>/dev/null | grep -q '"status":"UP"'; then
      echo "UP"
      return 0
    fi
    sleep 2
  done
  echo "앱이 뜨지 않았습니다. loadtest/stack.sh logs app 으로 확인하세요." >&2
  return 1
}

# 시드 컨테이너: 지금 떠 있는 것과 같은 이미지로, 포트를 열지 않고, JVM 힙 예약을 줄이고(기본 50%), 푸시를 끈 채 실행한다.
run_seeder() {
  mkdir -p "$OUT_DIR" && chmod 777 "$OUT_DIR"
  compose run --rm --no-deps -v "$OUT_DIR:/out" \
    -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=10 -XX:+ExitOnOutOfMemoryError" \
    -e FIREBASE_SERVICE_ACCOUNT_BASE64= \
    app --spring.profiles.active=loadtest "$@"
}

case "${1:-}" in
  build)
    docker build -t ohjumwhat:loadtest "$ROOT"
    ;;
  up)
    compose up -d
    wait_healthy
    compose exec -T db psql -U "$(db_env DB_USERNAME)" -d "$(db_env DB_NAME)" -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" >/dev/null
    echo "pg_stat_statements 준비됨. 앱 지표: curl -s http://127.0.0.1:8081/actuator/prometheus | head"
    ;;
  seed)
    orgs="${2:?조직 수}"; members="${3:?조직당 멤버 수}"; shift 3
    run_seeder --ohjumwhat.loadtest.mode=seed --ohjumwhat.loadtest.orgs="$orgs" --ohjumwhat.loadtest.members="$members" \
      --ohjumwhat.loadtest.out=/out/seed.json "$@"
    chmod 600 "$OUT_DIR/seed.json" "$OUT_DIR/seed.csv" 2>/dev/null || true
    echo "시드 완료: $OUT_DIR/seed.json (로그인 쿠키가 들어 있으니 공유하지 않는다)"
    ;;
  clean)
    run_seeder --ohjumwhat.loadtest.mode=clean
    rm -f "$OUT_DIR/seed.json" "$OUT_DIR/seed.csv"
    ;;
  psql)
    shift
    compose exec -T db psql -U "$(db_env DB_USERNAME)" -d "$(db_env DB_NAME)" "$@"
    ;;
  logs)
    shift
    compose logs --tail 200 "$@"
    ;;
  restart-app)
    compose restart app
    wait_healthy
    ;;
  down)
    shift
    compose down "$@"
    ;;
  *)
    sed -n '2,11p' "$0"
    exit 1
    ;;
esac
