// 점심 피크: 마감(seed.json의 closesAt) 기준으로 단계를 놓는다. 시드를 --ohjumwhat.loadtest.closes-in=PT6M 처럼 만들어 두고 바로 실행한다.
//   t=0            전원 동시 진입(상세·채팅 목록·소켓, 3초 폴링 유지)
//   마감 -60s ~ 0   참여 변경 폭주(5~10초마다)
//   마감 -5s ~ +10s 조직마다 한 명이 「지금 마감」, 나머지는 1초 간격으로 참여·취소 → 409 비율·p99
//   마감 ~ +3m      폴링만(마감 뒤 채팅은 1시간 열려 있다)
//
//   k6 run loadtest/lunch-peak.js -e BASE_URL=https://localhost   (VUS 기본 = 시드 멤버 전원, AFTER_MIN 기본 3)
import { sleep } from 'k6'
import exec from 'k6/execution'
import { closesAtMs, members, memberFor, orgs, memberOf } from './lib/seed.js'
import { login, paths, get, put, del, post, pick, jitter } from './lib/http.js'
import { openChat } from './lib/ws.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const closesIn = closesAtMs - Date.now()

/** init 코드는 요약 단계에서도 다시 돌므로 여기서 던지지 않고 setup()에서 확인한다. */
export function setup() {
  if (closesIn < 90_000) {
    throw new Error(`마감까지 ${Math.round(closesIn / 1000)}초뿐입니다. 시드를 --ohjumwhat.loadtest.closes-in=PT6M 으로 다시 만들어 주세요.`)
  }
}
const VUS = Number(__ENV.VUS || members.length)
const AFTER_MS = Number(__ENV.AFTER_MIN || 3) * 60 * 1000 // 마감 뒤 폴링을 유지할 분

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: {
    enter: {
      executor: 'constant-vus',
      exec: 'enter',
      vus: VUS,
      duration: `${Math.ceil((closesIn + AFTER_MS) / 1000)}s`,
      gracefulStop: '30s',
    },
    rush: {
      executor: 'constant-vus',
      exec: 'rush',
      vus: VUS,
      startTime: `${Math.max(0, Math.floor((closesIn - 60_000) / 1000))}s`,
      duration: '55s',
    },
    closing: {
      executor: 'constant-vus',
      exec: 'closing',
      vus: VUS,
      startTime: `${Math.max(0, Math.floor((closesIn - 5000) / 1000))}s`,
      duration: '15s',
    },
    closers: {
      executor: 'per-vu-iterations',
      exec: 'closer',
      vus: orgs.length,
      iterations: 1,
      startTime: `${Math.max(0, Math.floor((closesIn - 5000) / 1000))}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    unexpected_errors: ['count<10'],
    rt_detail: ['p(95)<800'],
    rt_vote: ['p(95)<1000'],
    rt_close: ['p(95)<2000'],
  },
}

export function enter() {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  get('me', p.me)
  get('today', p.today)
  get('detail', p.detail)
  get('messages', p.messages)
  const remaining = closesAtMs + AFTER_MS - Date.now()
  openChat(member, {
    ttlMs: Math.max(5000, remaining),
    intervals: [
      { ms: 3000, fn: () => get('detail', p.detail) },
      { ms: 30_000, fn: () => get('lettersUnread', p.lettersUnread) },
      { ms: 60_000, fn: () => get('guestbookAlerts', p.guestbookAlerts) },
    ],
  })
  sleep(1)
}

export function rush() {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  put('vote', p.vote, { optionId: pick(member.optionIds) }, { expected: [409] })
  sleep(jitter(7.5, 2.5))
}

export function closing() {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  if (Math.random() < 0.2) {
    del('unvote', p.unvote, { expected: [409] })
  } else {
    put('vote', p.vote, { optionId: pick(member.optionIds) }, { expected: [409] })
  }
  sleep(1)
}

/** 조직마다 멤버 한 명이 마감 2초 전에 「지금 마감」을 누른다(마감 직전 참여와 겹치게). */
export function closer() {
  const org = orgs[exec.vu.idInInstance % orgs.length]
  const member = memberOf(org, 0)
  const p = paths(member)
  login(member)
  const wait = closesAtMs - 2000 - Date.now()
  if (wait > 0) {
    sleep(wait / 1000)
  }
  post('close', p.close, undefined, { expected: [409] })
}

export const handleSummary = summaryFor('lunch-peak')
