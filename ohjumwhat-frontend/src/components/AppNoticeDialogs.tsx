import { useState } from 'react'
import { useLocation } from 'react-router'
import type { ReportResult, SanctionNotice } from '../lib/sanctions.ts'
import { type GuestbookWarning, useAckGuestbookWarnings, useGuestbookAlerts } from '../queries/guestbook.ts'
import { useMarkReportResultsSeen, useMarkSanctionsSeen, useSanctionAlerts } from '../queries/sanctions.ts'
import GuestbookWarningDialog from './GuestbookWarningDialog.tsx'
import ReportResultDialog from './ReportResultDialog.tsx'
import SanctionNoticeDialog from './SanctionNoticeDialog.tsx'

/** 지금 띄운 안내 */
type Current =
  | { kind: 'sanctions'; notices: SanctionNotice[] }
  | { kind: 'guestbook'; warnings: GuestbookWarning[] }
  | { kind: 'reportResults'; results: ReportResult[] }

/**
 * 어느 화면에서든 한 번 알릴 안내 창을 한 번에 하나만 띄운다(AppLayout): 제재 안내(00-S) → 방명록 글 제한 경고(07-M4) → 신고 처리 결과(00-R).
 * 처음 받았을 때와 화면을 옮길 때만 고르고(보는 도중에 폴링으로 받은 안내는 다음 화면에서), 하나를 끝까지 확인하면 다음 것을 바로 고른다.
 * 닫은 안내는 서버에 보낸 응답으로 캐시가 바뀌기 전에 다시 고르지 않게 여기서도 기억한다.
 */
export default function AppNoticeDialogs() {
  const location = useLocation()
  const sanctionAlerts = useSanctionAlerts()
  const guestbookAlerts = useGuestbookAlerts()
  const { mutate: markSeen } = useMarkSanctionsSeen()
  const { mutate: ack } = useAckGuestbookWarnings()
  const { mutate: markResultsSeen } = useMarkReportResultsSeen()
  // 마지막으로 고른 화면(location.key). null이면 지금 화면에서 다시 고른다.
  const [checkedKey, setCheckedKey] = useState<string | null>(null)
  const [current, setCurrent] = useState<Current | null>(null)
  const [seenIds, setSeenIds] = useState<ReadonlySet<number>>(() => new Set())
  const [ackedUntil, setAckedUntil] = useState(0)
  const [seenResultIds, setSeenResultIds] = useState<ReadonlySet<number>>(() => new Set())

  // 둘 다 받은 뒤에 고른다(한쪽이 실패해도 다른 쪽은 띄운다). 신고 결과는 제재 안내와 같은 응답에 온다.
  if (current === null && checkedKey !== location.key && !sanctionAlerts.isPending && !guestbookAlerts.isPending) {
    setCheckedKey(location.key)
    const notices = (sanctionAlerts.data?.sanctions ?? []).filter((n) => !seenIds.has(n.id))
    const warnings = (guestbookAlerts.data?.warnings ?? []).filter((w) => Date.parse(w.restrictedAt) > ackedUntil)
    const results = (sanctionAlerts.data?.reportResults ?? []).filter((r) => !seenResultIds.has(r.id))
    if (notices.length > 0) setCurrent({ kind: 'sanctions', notices })
    else if (warnings.length > 0) setCurrent({ kind: 'guestbook', warnings })
    else if (results.length > 0) setCurrent({ kind: 'reportResults', results })
  }

  // 「확인」으로 닫으면 <dialog>가 코드로 닫히며 close 이벤트가 한 번 더 온다. 그때는 이미 다른 안내(또는 없음)를
  // 고른 뒤라, 지금 띄운 종류가 아니면 무시한다(새로 고른 안내를 지우지 않게).
  const closeSanctions = (ids: number[], finished: boolean) => {
    if (current?.kind !== 'sanctions') return
    if (ids.length > 0) {
      markSeen(ids)
      setSeenIds((prev) => new Set([...prev, ...ids]))
    }
    setCurrent(null)
    // 끝까지 확인했으면 다음 안내를 바로, 도중에 닫았으면 다음 화면에서 고른다.
    if (finished) setCheckedKey(null)
  }

  const closeGuestbook = (until: string | null, finished: boolean) => {
    if (current?.kind !== 'guestbook') return
    if (until) {
      ack(until)
      setAckedUntil((prev) => Math.max(prev, Date.parse(until)))
    }
    setCurrent(null)
    // Esc·바깥으로 닫았으면 <dialog>가 먼저 닫혀 있어서, 같은 화면에서 바로 다시 고르면 열린 상태(open)가 그대로라
    // 창이 다시 보이지 않는다. 그래서 「확인」일 때만 바로 고르고, 아니면 다음 화면에서 고른다.
    if (finished) setCheckedKey(null)
  }

  const closeReportResults = (ids: number[], finished: boolean) => {
    if (current?.kind !== 'reportResults') return
    if (ids.length > 0) {
      markResultsSeen(ids)
      setSeenResultIds((prev) => new Set([...prev, ...ids]))
    }
    setCurrent(null)
    if (finished) setCheckedKey(null)
  }

  return (
    <>
      <SanctionNoticeDialog notices={current?.kind === 'sanctions' ? current.notices : null} onClose={closeSanctions} />
      <GuestbookWarningDialog warnings={current?.kind === 'guestbook' ? current.warnings : null} onClose={closeGuestbook} />
      <ReportResultDialog results={current?.kind === 'reportResults' ? current.results : null} onClose={closeReportResults} />
    </>
  )
}
