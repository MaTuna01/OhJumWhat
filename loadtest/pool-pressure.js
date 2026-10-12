// 커넥션 풀 압박: 메뉴 자동완성은 글자를 칠 때마다 조직 전체 기간을 집계하는 네이티브 CTE를 돈다(MenuStatsService.lastEatenByKey).
// 지난 투표가 많은 시드(--ohjumwhat.loadtest.history=500)에서 타이핑하는 사람 + 상세 폴링하는 사람을 함께 돌려
// hikaricp_connections_pending·acquire 시간·상세 p95를 본다(Tomcat 200 스레드 vs Hikari 10).
//   k6 run loadtest/pool-pressure.js -e TYPERS=30 -e POLLERS=50 -e DURATION=2m
import { sleep } from 'k6'
import { memberFor } from './lib/seed.js'
import { login, paths, get } from './lib/http.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const TYPERS = Number(__ENV.TYPERS || 30)
const POLLERS = Number(__ENV.POLLERS || 50)
const DURATION = __ENV.DURATION || '2m'
const WORDS = ['김', '김치', '김치찌', '김치찌개', '제', '제육', '돈', '돈까', '파', '파스타', '치', '치킨', '마', '마라', '냉', '냉면']

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: {
    typers: { executor: 'constant-vus', exec: 'typer', vus: TYPERS, duration: DURATION },
    pollers: { executor: 'constant-vus', exec: 'poller', vus: POLLERS, duration: DURATION, startTime: '5s' },
  },
  thresholds: { http_req_failed: ['rate<0.01'], rt_detail: ['p(95)<500'], rt_menuNames: ['p(95)<1000'] },
}

export function typer() {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  for (const word of WORDS) {
    get('menuNames', p.menuNames(word))
    sleep(0.3)
  }
}

export function poller() {
  const member = memberFor(TYPERS + __VU)
  const p = paths(member)
  login(member)
  get('detail', p.detail)
  sleep(3)
}

export const handleSummary = summaryFor('pool-pressure')
