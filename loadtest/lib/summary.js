// 실행 끝에 요약을 콘솔과 out/<이름>-<시각>.summary.json·.csv로 남긴다. CSV는 요청 이름별 p50/p95/p99·횟수·상태별 개수라
// docs/PERFORMANCE.md 표에 바로 옮긴다(report.py가 actuator 스냅샷과 합쳐 준다).
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js'
import { ROUTES } from './http.js'

/** Trend 요약에 p(99)와 count를 넣는다. 각 시나리오의 options에 `summaryTrendStats: TREND_STATS`로 둔다. */
export const TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count']

export function summaryFor(scenario) {
  return function handleSummary(data) {
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    const base = `${__ENV.OUT_DIR || 'out'}/${scenario}-${stamp}`
    const rows = [['name', 'count', 'avg_ms', 'p50_ms', 'p95_ms', 'p99_ms', 'max_ms']]
    for (const [key, name] of Object.entries(ROUTES)) {
      const m = data.metrics[`rt_${key}`]
      if (!m || !m.values || !m.values.count) {
        continue
      }
      const v = m.values
      rows.push([name, v.count, v.avg.toFixed(1), v.med.toFixed(1), v['p(95)'].toFixed(1), (v['p(99)'] || v['p(95)']).toFixed(1), v.max.toFixed(1)])
    }
    for (const name of ['http_req_duration', 'ws_connecting', 'ws_session_duration']) {
      const m = data.metrics[name]
      if (m && m.values && m.values.count) {
        const v = m.values
        rows.push([name, v.count, v.avg.toFixed(1), v.med.toFixed(1), v['p(95)'].toFixed(1), (v['p(99)'] || v['p(95)']).toFixed(1), v.max.toFixed(1)])
      }
    }
    for (const name of ['http_reqs', 'http_req_failed', 'expected_errors', 'unexpected_errors', 'ws_msgs_received', 'ws_handshake_failed', 'checks']) {
      const m = data.metrics[name]
      if (m && m.values) {
        const v = m.values
        rows.push([name, v.count !== undefined ? v.count : v.passes, (v.rate !== undefined ? v.rate : 0).toFixed(4), '', '', '', ''])
      }
    }
    const csv = rows.map((r) => r.join(',')).join('\n') + '\n'
    return {
      stdout: textSummary(data, { indent: ' ', enableColors: true }),
      [`${base}.summary.json`]: JSON.stringify(data, null, 1),
      [`${base}.csv`]: csv,
    }
  }
}
