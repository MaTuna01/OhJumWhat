import { useNow } from '../hooks/useNow.ts'
import { type ActiveSanction, activeRestrictions, endText, isActiveSanction, REASON_LABELS, RESTRICTION_LABELS } from '../lib/sanctions.ts'
import Badge from './Badge.tsx'

/**
 * Figma 이용 제한 카드(03-N7·D03-N7): 마이페이지 「내 정보」 바로 아래. 지금 걸린 제한을 종류별로(끝나는 시각과 함께),
 * 제재마다 사유와 관리자 설명을 보여준다. 걸린 제한이 없으면(기간이 지나 풀렸어도) 그리지 않는다.
 */
export default function RestrictionCard({ sanctions }: { sanctions: ActiveSanction[] }) {
  const now = useNow()
  const active = sanctions.filter((s) => isActiveSanction(s, now))
  if (active.length === 0) return null
  const rows = activeRestrictions(active, now)
  const suspended = rows.some((row) => row.type === 'SUSPEND')

  return (
    <section aria-labelledby="restriction-card-title" className="space-y-3 rounded-2xl border border-border-default bg-bg-surface p-5">
      <div className="flex items-center gap-2">
        <h2 id="restriction-card-title" className="font-bold">
          이용 제한
        </h2>
        <Badge tone="danger">제한 중</Badge>
      </div>
      <ul className="space-y-1.5 rounded-xl bg-bg-muted px-3.5 py-3">
        {rows.map(({ type, endsAt }) => (
          <li key={type} className="flex items-baseline justify-between gap-3">
            <span className="text-sm font-bold">{RESTRICTION_LABELS[type]}</span>
            <span className="shrink-0 text-xs text-text-secondary">{endText(endsAt)}</span>
          </li>
        ))}
      </ul>
      {active.map((sanction) => (
        <div key={sanction.id}>
          <p className="text-xs text-text-tertiary">사유 · {REASON_LABELS[sanction.reason]}</p>
          {sanction.note && <p className="mt-0.5 text-sm break-words whitespace-pre-wrap">{sanction.note}</p>}
        </div>
      ))}
      <p className="text-xs text-text-tertiary">
        {suspended
          ? '활동이 정지된 동안에는 올라온 투표에 참여만 할 수 있어요. 메뉴 올리기·댓글·채팅·쪽지·방명록·프로필 수정은 할 수 없어요.'
          : '기간이 끝나면 자동으로 풀려요. 투표 참여는 그대로 할 수 있어요.'}
      </p>
    </section>
  )
}
