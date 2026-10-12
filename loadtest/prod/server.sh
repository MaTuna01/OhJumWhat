#!/usr/bin/env bash
# 운영 서버(~/ohjumwhat)에서 쓰는 부하 측정 보조 명령. PC에서 이 파일과 collect.sh를 ~/ohjumwhat/loadtest/ 로 올린다:
#   scp -i ohjumwhat.pem loadtest/prod/server.sh loadtest/collect.sh <사용자>@1.201.114.178:~/ohjumwhat/loadtest/
#
#   loadtest/server.sh baseline            nproc·메모리·컨테이너 사용량·이미지 버전 기록 → loadtest-out/baseline-<시각>.txt
#   loadtest/server.sh idle                최근 15분 접속 세션 수·채팅 연결 수(0이어야 측정한다)
#   loadtest/server.sh seed 15 20 [시드 인자…]   예: seed 15 20 --ohjumwhat.loadtest.closes-in=PT8M
#   loadtest/server.sh clean               시드 데이터 정리 + 검증 SQL(모두 0이어야 한다)
#   loadtest/server.sh verify              검증 SQL만
#   loadtest/server.sh collect <이름>      지표 수집 시작(백그라운드). 끝낼 때 collect-stop
#   loadtest/server.sh collect-stop
#   loadtest/server.sh watch               앱 로그에서 오류·커넥션 대기만 따라 본다
#
# 시드 컨테이너는 지금 떠 있는 것과 같은 이미지(.env의 APP_IMAGE)로, 포트를 열지 않고, JVM 힙 예약을 줄이고(기본 50%면
# 서버 RAM 절반), 푸시를 끈 채 돈다. loadtest 프로필이 없는 옛 이미지(v1.11.5 이하)면 서버가 그냥 떠 버리므로 120초에 끊는다.
set -euo pipefail

cd "$(dirname "$0")/.."    # ~/ohjumwhat
OUT="$PWD/loadtest-out"
mkdir -p "$OUT" && chmod 777 "$OUT"
db_env() { grep -E "^$1=" .env | cut -d= -f2-; }
psql_db() { docker compose exec -T db psql -U "$(db_env DB_USERNAME)" -d "$(db_env DB_NAME)" "$@"; }

run_seeder() {
  timeout 120 docker compose run --rm --no-deps -v "$OUT:/out" \
    -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=10 -XX:+ExitOnOutOfMemoryError" \
    -e FIREBASE_SERVICE_ACCOUNT_BASE64= \
    app --spring.profiles.active=loadtest "$@"
  local code=$?
  if [[ $code -eq 124 ]]; then
    echo "120초 안에 끝나지 않았습니다. 이미지에 loadtest 프로필이 없는 것 같습니다(APP_IMAGE=$(grep ^APP_IMAGE .env)). 시드 명령(#141)이 든 버전을 배포한 뒤 다시 하세요." >&2
  fi
  return $code
}

verify() {
  echo "== 부하 테스트 데이터 잔여(모두 0이어야 한다)"
  psql_db -Atc "
    select 'users', count(*) from users where google_sub like 'loadtest-%'
    union all select 'organizations', count(*) from organizations where name like '[부하테스트] %'
    union all select 'spring_session', count(*) from spring_session where principal_name like 'loadtest-%'
    union all select 'polls', count(*) from polls where title like '[부하테스트]%'"
}

case "${1:-}" in
  baseline)
    f="$OUT/baseline-$(date +%Y%m%d-%H%M).txt"
    { echo "# $(date)"; echo "nproc=$(nproc)"; free -h; echo; swapon --show; echo; docker stats --no-stream; echo; df -h /; echo
      grep ^APP_IMAGE .env; docker compose images app; curl -s https://$(grep ^APP_DOMAIN .env | cut -d= -f2)/version.json; echo
      curl -s http://127.0.0.1:8081/actuator/prometheus | grep -E "^(hikaricp_connections_(active|max|pending)|ohjumwhat_chat_connections|tomcat_threads_config_max|jvm_memory_max_bytes\{area=\"heap\",id=\"G1 Old Gen\")"
    } | tee "$f"
    echo "저장: $f"
    ;;
  idle)
    psql_db -Atc "select '최근 15분 접속 세션', count(*) from spring_session where last_access_time > (extract(epoch from now())*1000 - 15*60*1000)"
    curl -s http://127.0.0.1:8081/actuator/prometheus | grep -E "^ohjumwhat_chat_connections"
    ;;
  seed)
    orgs="${2:?조직 수}"; members="${3:?조직당 멤버 수}"; shift 3
    run_seeder --ohjumwhat.loadtest.mode=seed --ohjumwhat.loadtest.orgs="$orgs" --ohjumwhat.loadtest.members="$members" \
      --ohjumwhat.loadtest.out=/out/seed.json "$@"
    chmod 600 "$OUT/seed.json" "$OUT/seed.csv"
    echo "시드 완료: $OUT/seed.json — PC로 가져간다: scp -i ohjumwhat.pem <사용자>@<IP>:~/ohjumwhat/loadtest-out/seed.json loadtest/out/prod-seed.json"
    ;;
  clean)
    run_seeder --ohjumwhat.loadtest.mode=clean
    rm -f "$OUT/seed.json" "$OUT/seed.csv"
    verify
    ;;
  verify)
    verify
    ;;
  collect)
    name="${2:?수집 이름}"
    STACK=prod RUN="$name" LOADTEST_ENV_FILE="$PWD/.env" nohup "$PWD/loadtest/collect.sh" > "$OUT/collect-$name.log" 2>&1 &
    echo $! > "$OUT/collect.pid"
    echo "수집 시작(pid $(cat "$OUT/collect.pid")): $PWD/loadtest/out/$name — 끝낼 때 loadtest/server.sh collect-stop"
    ;;
  collect-stop)
    if [[ -f "$OUT/collect.pid" ]]; then kill -TERM "$(cat "$OUT/collect.pid")" && rm -f "$OUT/collect.pid" && echo "수집을 끝냈습니다(pg_stat_statements는 운영에 없어 pg-top.txt는 비어 있다)."; else echo "수집 중이 아닙니다."; fi
    ;;
  watch)
    docker compose logs -f --since 1m app | grep --line-buffered -iE "error|exception|timeout|pending|not available|OutOfMemory"
    ;;
  *)
    sed -n '2,14p' "$0"; exit 1
    ;;
esac
