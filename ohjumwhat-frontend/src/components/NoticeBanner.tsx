import { Link } from 'react-router'
import { useMarkNoticesSeen, useUnreadNotices } from '../queries/notices.ts'
import NoticeBadge from './NoticeBadge.tsx'

/**
 * 조직 홈(속한 조직이 없으면 마이페이지) 맨 위 한 줄(Figma 04-B): 안 읽은 새 소식 중 가장 최근 것.
 * 줄을 누르면 「새 소식」으로 가고, ✕는 모두 읽은 것으로 한다. 화면을 가리지 않도록 알림(alert)으로 읽지 않는다.
 */
export default function NoticeBanner() {
  const { data } = useUnreadNotices()
  const markSeen = useMarkNoticesSeen()
  if (!data?.latest) return null
  const { latest } = data
  const rest = data.count - 1

  return (
    <div className="flex items-center rounded-xl bg-bg-brand-soft pr-1.5">
      <Link
        to="/notices"
        className="flex min-w-0 flex-1 items-center gap-2 rounded-xl py-3 pl-3.5 focus-visible:outline-2 focus-visible:outline-border-brand"
      >
        <NoticeBadge kind={latest.kind} version={latest.version} short />
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{latest.title}</span>
        {rest > 0 && <span className="shrink-0 text-xs text-text-tertiary">외 {rest}개</span>}
        <span className="shrink-0 text-lg leading-none font-bold text-icon-muted" aria-hidden>
          ›
        </span>
      </Link>
      <button
        type="button"
        aria-label="새 소식 닫기"
        onClick={() => markSeen.mutate()}
        className="ml-1 grid size-8 shrink-0 place-items-center rounded-lg text-sm text-icon-muted hover:bg-bg-brand-muted hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-border-brand"
      >
        <span aria-hidden>✕</span>
      </button>
    </div>
  )
}
