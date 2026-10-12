// 정상 부하: 「진행 중인 투표 상세 화면을 열어 둔 사람」을 VU 하나로 흉내 내며 100 → 300 → 1,000명으로 올린다.
// 한 사람의 요청 비율은 프론트와 같다(src/queries/polls.ts 3초, letters.ts 30초, guestbook.ts·sanctions.ts 60초, build.ts 5분).
//
//   k6 run loadtest/steady.js -e BASE_URL=https://localhost -e MAX_VUS=300 -e HOLD=5m
//
// 환경변수: SEED(시드 파일), BASE_URL, MAX_VUS(100|300|1000, 기본 1000), HOLD(단계 유지 시간, 기본 300→10m), SESSION_MIN(한 번 머무는 분, 기본 10)
import { sleep } from 'k6'
import { memberFor } from './lib/seed.js'
import { login, paths, get, put, del, post, pick, jitter } from './lib/http.js'
import { openChat } from './lib/ws.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const MAX_VUS = Number(__ENV.MAX_VUS || 1000)
const HOLD = __ENV.HOLD || '10m' // 단계마다 유지 시간(100명 단계도 같다). 스모크는 HOLD=1m
const SESSION_MS = Number(__ENV.SESSION_MIN || 10) * 60 * 1000

const stages = []
for (const [target, ramp] of [
  [100, '1m'],
  [300, '1m'],
  [1000, '2m'],
]) {
  if (target > MAX_VUS) {
    break
  }
  stages.push({ duration: ramp, target }, { duration: HOLD, target })
}
stages.push({ duration: '1m', target: 0 })

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: { steady: { executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '30s', gracefulStop: '30s' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    unexpected_errors: ['count<10'],
    rt_detail: ['p(95)<500'],
    rt_vote: ['p(95)<800'],
    rt_messages: ['p(95)<800'],
    ws_connecting: ['p(95)<1000'],
  },
}

export default function () {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)

  // 진입: 화면이 처음 그릴 때 받는 것들
  get('me', p.me)
  get('org', p.org)
  get('today', p.today)
  const detail = get('detail', p.detail)
  const messages = get('messages', p.messages)
  if (detail.status !== 200) {
    sleep(3)
    return
  }
  let lastMessageId = lastIdOf(messages)
  let closed = detail.json('status') === 'CLOSED'

  openChat(member, {
    ttlMs: jitter(SESSION_MS, SESSION_MS / 4),
    onMessage: (socket, data) => {
      try {
        const event = JSON.parse(data)
        if (event.message && event.message.id > lastMessageId) {
          lastMessageId = event.message.id
        }
      } catch (e) {
        // ping 등
      }
    },
    intervals: [
      {
        ms: 3000,
        fn: (socket) => {
          const res = get('detail', p.detail)
          if (res.status === 200 && res.json('status') === 'CLOSED') {
            closed = true
          }
        },
      },
      { ms: 30_000, fn: () => get('lettersUnread', p.lettersUnread) },
      { ms: 60_000, fn: () => get('guestbookAlerts', p.guestbookAlerts) },
      { ms: 60_000, fn: () => get('sanctionAlerts', p.sanctionAlerts) },
      { ms: 300_000, fn: () => get('version', p.version) },
      {
        ms: 5000,
        fn: () => {
          if (lastMessageId > 0) {
            put('markRead', p.markRead, { lastReadId: lastMessageId }, { ok: [204] })
          }
        },
      },
      {
        // 90±30초마다 참여 변경(10%는 취소). 마감 뒤에는 409가 기대한 응답이다.
        ms: 30_000,
        fn: () => {
          if (Math.random() > 1 / 3) {
            return
          }
          if (Math.random() < 0.1) {
            del('unvote', p.unvote, { expected: [409] })
          } else {
            put('vote', p.vote, { optionId: pick(member.optionIds) }, { expected: [409] })
          }
        },
      },
      {
        // 120±60초마다 채팅 한 줄(10초 10개 제한 안). 채팅이 닫히면 409.
        ms: 60_000,
        fn: () => {
          if (Math.random() < 0.5) {
            post('sendMessage', p.sendMessage, { body: `부하 테스트 ${__VU} ${Date.now()}` }, { ok: [200, 201], expected: [409, 429] })
          }
        },
      },
      {
        // 30분에 한 번꼴로 메뉴 추가(고유 이름)
        ms: 60_000,
        fn: () => {
          if (!closed && Math.random() < 1 / 30) {
            post('addOption', p.addOption, { name: `메뉴-${__VU}-${Date.now() % 100000}` }, { ok: [200, 201], expected: [409] })
          }
        },
      },
    ],
  })
  sleep(1)
}

function lastIdOf(res) {
  if (res.status !== 200) {
    return 0
  }
  const list = res.json('messages')
  return Array.isArray(list) && list.length ? list[list.length - 1].id : 0
}

export const handleSummary = summaryFor('steady')
