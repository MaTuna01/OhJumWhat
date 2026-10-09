// 마감 vs 참여 경합(통계 관찰): 조직마다 20초 창을 차례로 쓴다. 멤버들이 10초 동안 메뉴를 바꾸고, 10초째에 한 명이 「지금 마감」을 누른 뒤
// 3초 뒤 상세를 다시 받아 마감 응답의 명단과 다르면(마감 뒤 커밋된 참여) 발생으로 센다. 조직 수만큼 반복된다(시드 orgs=20이면 20회).
//   k6 run loadtest/close-race.js -e RUNS=20   (RUNS 기본 = 시드 조직 수)
// 결정적 재현은 통합 테스트(PollCloseConcurrencyTest)가 맡고, 여기서는 발생 빈도만 본다.
import { sleep } from 'k6'
import { Counter } from 'k6/metrics'
import exec from 'k6/execution'
import { orgs, membersPerOrg, memberOf } from './lib/seed.js'
import { login, paths, get, put, post, pick } from './lib/http.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const WINDOW_MS = 20_000
const VOTE_MS = 10_000
const detected = new Counter('close_race_detected')
const runs = new Counter('close_race_runs')
const startAt = Date.now() + 5000
const RUNS = Math.min(orgs.length, Number(__ENV.RUNS || orgs.length))

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: {
    race: { executor: 'per-vu-iterations', vus: membersPerOrg, iterations: RUNS, maxDuration: `${Math.ceil((RUNS * WINDOW_MS) / 1000) + 30}s` },
  },
}

export default function () {
  const run = exec.scenario.iterationInInstance % RUNS
  const org = orgs[run]
  const member = memberOf(org, (__VU - 1) % membersPerOrg)
  const p = paths(member)
  login(member)
  const windowStart = startAt + run * WINDOW_MS
  sleep(Math.max(0, windowStart - Date.now()) / 1000)

  if (__VU === 1) {
    // 마감 담당: 10초 기다렸다가 마감하고 3초 뒤 명단을 비교한다
    sleep(VOTE_MS / 1000)
    const closeRes = post('close', p.close, undefined, { expected: [409] })
    sleep(3)
    const after = get('detail', p.detail)
    runs.add(1)
    if (closeRes.status === 200 && after.status === 200 && voters(closeRes) !== voters(after)) {
      detected.add(1)
      console.warn(`마감 뒤 명단이 바뀜: org=${org.orgId} poll=${org.pollId}\n  마감 응답: ${voters(closeRes)}\n  3초 뒤: ${voters(after)}`)
    }
    return
  }
  // 참여자: 창이 끝날 때까지 메뉴를 바꾼다(마감 뒤 409는 기대한 응답)
  while (Date.now() < windowStart + VOTE_MS + 1500) {
    put('vote', p.vote, { optionId: pick(member.optionIds) }, { expected: [409] })
  }
}

function voters(res) {
  const options = res.json('options') || []
  return options
    .map((o) => `${o.id}:${(o.voters || []).map((v) => v.userId).sort((a, b) => a - b).join('/')}`)
    .join(' ')
}

export const handleSummary = summaryFor('close-race')
