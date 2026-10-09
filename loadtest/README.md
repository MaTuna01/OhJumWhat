# 부하 테스트 (k6)

운영 서버(가비아, 한 대)가 얼마나 수용하는지와 마감 순간 같은 동시성 문제를 재는 도구다(이슈 #140, Notion 「29. 서버 부하테스트」). 결과 보고서는 `docs/PERFORMANCE.md`다.

- 부하 도구는 **k6**다(`brew install k6`). 시나리오가 JS라 프론트의 폴링 비율(`src/queries/*.ts`)을 그대로 옮기고, WebSocket과 HTTP 폴링을 VU 하나에서 섞을 수 있고, 1,000 VU를 PC에서 감당한다. 교차 검증이 필요하면 Gatling(Java DSL, Gradle 플러그인)으로 `steady` 하나를 재현해 비교한다.
- 구글 로그인은 자동화할 수 없어서 **시드 명령**(`ohjumwhat-backend`의 `loadtest` 프로필)이 테스트 조직·회원·투표와 로그인 세션을 만들고 `out/seed.json`에 쿠키를 적는다. **이 파일은 로그인 쿠키 묶음이다.** 공유·커밋하지 않고(gitignore) 끝나면 지운다.

## 1. 로컬 복제 스택

운영 compose(`deploy/docker-compose.yml`: Caddy → 앱 → PostgreSQL)를 그대로 쓰고 `docker-compose.limits.yml`로 CPU·메모리 한도와 DB 측정 도구(`pg_stat_statements`, 느린 쿼리 로그, 커밋 시각, 잠금 대기 로그)를 얹는다.

```bash
cp loadtest/.env.local.example loadtest/.env.local   # LOADTEST_*를 운영 서버의 nproc·free -h에 맞춘다
loadtest/stack.sh build                               # 운영 Dockerfile로 ohjumwhat:loadtest
loadtest/stack.sh up                                  # https://localhost (Caddy 내부 CA), 관리 포트 127.0.0.1:8081
loadtest/stack.sh seed 15 20                          # 조직 15 × 멤버 20, 마감은 3시간 뒤
loadtest/stack.sh seed 50 20 --ohjumwhat.loadtest.history=500 --ohjumwhat.loadtest.closes-in=PT6M
loadtest/stack.sh clean
loadtest/stack.sh psql -c "select count(*) from spring_session"
loadtest/stack.sh down -v
```

시드 인자(모두 `--ohjumwhat.loadtest.*`): `orgs`, `members`, `closes-at=HH:mm`(오늘 KST) 또는 `closes-in=PT30M`(기본 3시간), `prevote`(미리 참여 비율, 기본 0.5), `history`(조직마다 지난 투표 일수, 기본 0). 같은 데이터가 남아 있으면 시드를 거절하니 먼저 `clean`한다.

## 2. 시나리오

모든 스크립트는 `BASE_URL`(기본 `https://localhost`), `SEED`(기본 `loadtest/out/seed.json`), `OUT_DIR`(기본 `out`)을 받고 끝나면 `out/<이름>-<시각>.summary.json`·`.csv`를 남긴다. 기대한 오류(마감 409, 속도 제한 429, 사진 503)는 `expected_errors`로 따로 세고 `http_req_failed`에 넣지 않는다. `cd loadtest` 뒤 실행한다(`open()` 경로가 스크립트 기준이다).

| 스크립트 | 무엇을 보나 | 예 |
|---|---|---|
| `steady.js` | 정상 부하. 「상세 화면을 10분 보는 사람」(3초 상세, 30초 쪽지, 60초 알림, WS 1개, 가끔 참여·채팅·메뉴)을 100 → 300 → 1,000명으로 | `k6 run steady.js -e MAX_VUS=300 -e HOLD=5m` |
| `lunch-peak.js` | 점심 피크. 시드의 마감 시각 기준으로 전원 진입 → 마감 -60초 참여 폭주 → 마감 ±10초 「지금 마감」+참여·취소 → 마감 뒤 폴링 | 시드를 `closes-in=PT6M`로 만든 직후 `k6 run lunch-peak.js` |
| `focus-burst.js` | 탭 포커스 버스트(staleTime 0 쿼리 9개 동시) × N명 → Hikari pending·Tomcat busy | `k6 run focus-burst.js -e VUS=300` |
| `reconnect-storm.js` | 앱 재시작 직후 모든 화면이 같은 순간 WS 재연결 + `GET /messages` | `loadtest/stack.sh restart-app && k6 run reconnect-storm.js -e VUS=300 -e SPREAD=1s` |
| `photo.js` | 채팅 사진 동시 처리(세마포어 2 → 503)·프로필 사진(제한 없음) → 힙·GC | `k6 run photo.js -e VUS=10 -e PHOTOS=2 -e MODE=both` |
| `pool-pressure.js` | 자동완성(조직 전체 기간 CTE) 타이핑 + 상세 폴링 → 커넥션 풀 고갈. `history=500` 시드에서 | `k6 run pool-pressure.js -e TYPERS=30 -e POLLERS=50` |
| `session-burst.js` | 한 세션으로 동시 요청 → `spring_session.last_access_time` 행 잠금 대기 | `k6 run session-burst.js -e VUS=20` |
| `close-race.js` | 마감 vs 참여 경합 발생 빈도(조직마다 20초 창, 마감 응답 명단과 3초 뒤 명단 비교) | `orgs=20` 시드에서 `k6 run close-race.js` |

한 사람의 요청 비율 근거: 투표 상세 3초(`queries/polls.ts`), 쪽지 안 읽은 수 30초(`letters.ts`), 방명록·제재 알림 60초(`guestbook.ts`·`sanctions.ts`), `version.json` 5분(`build.ts`), 읽음 PUT은 메시지가 올 때 최대 1초 1건(여기서는 5초), 참여 변경은 90±30초, 채팅은 120±60초로 두었다.

## 3. 지표 수집과 보고서

```bash
RUN=steady-300 loadtest/collect.sh &      # 10초마다 actuator 스냅샷·docker stats·pg_stat_activity 샘플
k6 run steady.js -e MAX_VUS=300
kill %1                                   # 끝날 때 pg_stat_statements 상위 25개·느린 쿼리·잠금 로그 저장
python3 loadtest/report.py loadtest/out/steady-300 loadtest/out/steady-<시각>.csv   # 마크다운 표
```

`report.py`는 k6 요청 이름별 p50/p95/p99, actuator 차분으로 uri·status별 초당 요청·평균 응답, 스냅샷 최대값(Hikari active/pending·힙·Tomcat busy·채팅 연결·GC pause)을 표로 낸다. `docs/PERFORMANCE.md`에 그대로 붙인다. 측정 전에 `loadtest/stack.sh psql -c "select pg_stat_statements_reset()"`로 통계를 비운다.

측정 순서(로컬): `focus-burst 100` → `steady`(100→300, 먼저 `-e HOLD=2m` 스모크) → `lunch-peak`(시드 `closes-in=PT6M` 재생성) → `reconnect-storm 300` → `photo` → `pool-pressure`·`session-burst`(`history=500` 시드) → `close-race`(`orgs=20`) → `clean` → `seed 50 20`으로 1,000명. Caddy 경유(`https://localhost`)와 앱 직접(`docker run --network ohjumwhat-loadtest_default grafana/k6 … -e BASE_URL=http://app:8080`) 비교는 한 번.

k6와 앱이 같은 PC면 k6의 CPU가 결과를 흐린다. `docker stats`의 app·db CPU와 PC 전체 사용량을 함께 적는다.

## 4. 운영 서버 심야 측정 (1회, 300명까지)

로컬 측정을 끝낸 뒤, 사용자가 없는 심야에 한 번만 한다. 1,000명은 로컬 300명 결과에 여유(Hikari pending 0, 힙 70% 미만, p95 목표 안)가 있을 때 다른 날 따로 정한다. 사진 시나리오는 운영에서 생략한다.

1. 서버: `./backup.sh` → `nproc; free -h; docker stats --no-stream` 기준선 기록.
2. 사용자 없음 확인: `docker compose exec db psql -U ohjumwhat -d ohjumwhat -c "select count(*) from spring_session where last_access_time > (extract(epoch from now())*1000 - 15*60*1000)"`가 0, Netdata의 `ohjumwhat_chat_connections`가 0.
3. UptimeRobot은 끄지 않는다. 알림이 오면 중단한다.
4. 시드(서버, 지금 떠 있는 것과 같은 `APP_IMAGE`):
   ```bash
   cd ~/ohjumwhat && mkdir -p loadtest-out && chmod 777 loadtest-out
   docker compose run --rm --no-deps -v "$PWD/loadtest-out:/out" \
     -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=10 -XX:+ExitOnOutOfMemoryError" -e FIREBASE_SERVICE_ACCOUNT_BASE64= \
     app --spring.profiles.active=loadtest --ohjumwhat.loadtest.mode=seed \
         --ohjumwhat.loadtest.orgs=15 --ohjumwhat.loadtest.members=20 --ohjumwhat.loadtest.out=/out/seed.json
   chmod 600 loadtest-out/seed.json
   ```
   `JAVA_TOOL_OPTIONS`를 꼭 줄인다(기본 50%면 시드 JVM이 서버 RAM 절반을 예약한다).
5. k6는 **개발자 PC에서** `scp`로 받은 `seed.json`으로 `-e BASE_URL=https://www.ohjumwhat.cloud -e SEED=/경로/seed.json` 실행한다(서버 안에서 돌리면 k6가 서버 CPU를 써 측정이 흐려진다). 서버에는 `STACK=prod RUN=prod-… ~/ohjumwhat/loadtest/collect.sh`(파일을 `scp`로 올린다)와 `docker compose logs -f app`을 띄워 둔다.
6. 순서: `focus-burst -e VUS=100` → `steady -e MAX_VUS=300 -e HOLD=5m` → `lunch-peak`(시드를 `closes-in=PT8M`로 다시) → `reconnect-storm -e VUS=300`(앱 재시작은 하지 않고 연결만).
7. **중단 기준**(하나라도 해당하면 k6 Ctrl-C → 즉시 정리): `http_req_failed` 2%가 1분 지속, p95 2초 초과, `hikaricp_connections_pending > 0`이 30초 지속, 힙 85% 초과, 스왑 증가, `/healthz` DOWN, UptimeRobot 알림.
8. 정리: `docker compose run --rm --no-deps -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=10" -e FIREBASE_SERVICE_ACCOUNT_BASE64= app --spring.profiles.active=loadtest --ohjumwhat.loadtest.mode=clean` 종료 코드 0 확인 → 검증 SQL이 모두 0:
   ```sql
   select count(*) from users where google_sub like 'loadtest-%';
   select count(*) from organizations where name like '[부하테스트] %';
   select count(*) from spring_session where principal_name like 'loadtest-%';
   select count(*) from polls where title like '[부하테스트]%';
   ```
   → 관리자 콘솔 개요(회원·조직·오늘 투표 수)가 측정 전 값인지 → `seed.json` 삭제(서버·PC) → 다음 날 `backup.log`와 Netdata 시계열 수(`docs/DEPLOY.md` 「4. 앱 지표 수집」의 한도) 확인.
