#!/usr/bin/env bash
# 운영 서버 심야 측정(PC에서 실행). 300명까지만, 단계 사이마다 멈춰서 확인을 받는다.
#   loadtest/prod/run.sh                       (BASE_URL 기본 https://www.ohjumwhat.cloud, SEED 기본 loadtest/out/prod-seed.json)
#   STEP=steady loadtest/prod/run.sh           한 단계만: focus | steady | lunch | reconnect
#
# 먼저 서버에서: loadtest/server.sh baseline → idle(0 확인) → seed 15 20 → seed.json을 scp로 가져온다 → server.sh collect <이름>.
# 중단 기준(하나라도): k6 thresholds 초과(종료 코드 99)·/healthz DOWN·오류율 2%·UptimeRobot 알림. 멈추면 바로 서버에서 clean.
# 이 스크립트는 15초마다 /healthz를 보고 DOWN이면 k6를 끝낸다.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT/loadtest"
BASE_URL="${BASE_URL:-https://www.ohjumwhat.cloud}"
SEED="${SEED:-$ROOT/loadtest/out/prod-seed.json}"
MIN_VERSION="${MIN_VERSION:-1.11.6}"   # 시드 명령(#141)이 든 첫 릴리스. 다르면 바꿔 준다
CURL="${CURL:-curl -s --max-time 10}"   # 로컬 스택 리허설은 CURL="curl -sk --max-time 10" (내부 CA)
STAMP=$(date +%Y%m%d-%H%M)
OUT_DIR="$ROOT/loadtest/out/prod-$STAMP"
mkdir -p "$OUT_DIR"
export OUT_DIR

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ask() { read -r -p "$* [y/N] " a; [[ "$a" == "y" || "$a" == "Y" ]]; }
vergte() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -1)" == "$2" ]]; }

preflight() {
  say "사전 점검"
  command -v k6 >/dev/null || { echo "k6가 없습니다(brew install k6)."; exit 1; }
  [[ -f "$SEED" ]] || { echo "시드 파일이 없습니다: $SEED (서버의 loadtest-out/seed.json을 scp로 가져온다)"; exit 1; }
  local health; health=$($CURL "$BASE_URL/healthz")
  echo "healthz: $health"; [[ "$health" == *'"status":"UP"'* ]] || { echo "앱이 UP이 아닙니다."; exit 1; }
  local ver; ver=$($CURL "$BASE_URL/version.json" | sed -E 's/.*"version":"([^"]+)".*/\1/')
  echo "운영 버전: $ver (시드 명령은 $MIN_VERSION 이상에 있다)"
  vergte "$ver" "$MIN_VERSION" || { echo "운영 버전이 낮습니다. 시드 명령이 든 버전을 먼저 배포하세요."; exit 1; }
  local members closes; members=$(python3 -c "import json;d=json.load(open('$SEED'));print(sum(len(o['members']) for o in d['orgs']))")
  closes=$(python3 -c "import json;print(json.load(open('$SEED'))['closesAt'])")
  echo "시드: 회원 $members명, 마감 $closes"
  [[ "$members" -le 300 ]] || { echo "운영은 300명까지만 합니다(시드가 $members명)."; exit 1; }
  echo "결과 폴더: $OUT_DIR"
  ask "서버에서 baseline·idle(0)·collect를 켰고, UptimeRobot 알림을 볼 수 있습니까?" || exit 1
}

# /healthz를 15초마다 보고 DOWN이면 k6를 끝낸다
watchdog() {
  while true; do
    sleep 15
    local h; h=$($CURL "$BASE_URL/healthz")
    if [[ "$h" != *'"status":"UP"'* ]]; then
      echo "!! /healthz가 UP이 아닙니다($h). k6를 멈춥니다." | tee -a "$OUT_DIR/watchdog.log"
      pkill -INT -f "k6 run" || true
    fi
  done
}

run_k6() { # name script args…
  local name="$1"; shift
  say "$name"
  watchdog & local wd=$!
  k6 run "$@" -e BASE_URL="$BASE_URL" -e SEED="$SEED" 2>&1 | tee "$OUT_DIR/$name.log" | grep -vE "running \(|\[ *[0-9]+% *\]" | tail -40
  local code=${PIPESTATUS[0]}
  kill "$wd" 2>/dev/null; wait "$wd" 2>/dev/null
  echo "k6 종료 코드: $code (0 정상, 99 thresholds 초과)"
  if [[ $code -ne 0 ]]; then
    ask "thresholds를 넘었거나 멈췄습니다. 서버 지표를 보고 계속할까요?" || { echo "중단. 서버에서 loadtest/server.sh clean 을 실행하세요."; exit 2; }
  fi
}

STEP="${STEP:-all}"
preflight
if [[ "$STEP" == all || "$STEP" == focus ]]; then
  run_k6 prod-focus-100 focus-burst.js -e VUS=100 -e ROUNDS=2
fi
if [[ "$STEP" == all || "$STEP" == steady ]]; then
  run_k6 prod-steady-300 steady.js -e MAX_VUS=300 -e HOLD=5m
fi
if [[ "$STEP" == all || "$STEP" == lunch ]]; then
  say "점심 피크 준비"
  echo "서버에서: loadtest/server.sh clean && loadtest/server.sh seed 15 20 --ohjumwhat.loadtest.closes-in=PT8M"
  echo "그다음 새 seed.json을 scp로 $SEED 에 덮어쓴다. (마감 8분 뒤 기준으로 단계가 잡히므로 2분 안에 시작한다)"
  ask "시드를 다시 만들고 가져왔습니까?" || exit 2
  run_k6 prod-lunch-peak-300 lunch-peak.js -e AFTER_MIN=2
fi
if [[ "$STEP" == all || "$STEP" == reconnect ]]; then
  say "재연결 폭풍(앱 재시작 없이 연결만 몰아준다)"
  run_k6 prod-reconnect-300 reconnect-storm.js -e VUS=300 -e SPREAD=1s -e HOLD_SEC=60
fi
say "끝. 서버에서 loadtest/server.sh collect-stop && loadtest/server.sh clean 을 실행하고, 검증 SQL이 모두 0인지, 관리자 콘솔 개요 수치가 돌아왔는지 본다."
echo "결과: $OUT_DIR, 서버의 loadtest/out/<이름>을 scp로 가져와 python3 loadtest/report.py <폴더> <csv> 로 표를 만든다."
