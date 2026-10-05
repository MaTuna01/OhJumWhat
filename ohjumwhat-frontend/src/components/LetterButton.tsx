import { Link } from 'react-router'
import { unreadLetterLabel } from '../lib/letters.ts'
import { useUnreadLetters } from '../queries/letters.ts'
import { UnreadBadge } from './ChatUnread.tsx'

/** 상단 바 쪽지(Figma TopBar 「쪽지 배지」): 봉투 아이콘과 안 읽은 쪽지 수. 누르면 쪽지함(/letters) */
export default function LetterButton() {
  const { data } = useUnreadLetters()
  const count = data?.count ?? 0

  return (
    <Link
      to="/letters"
      aria-label={count > 0 ? `쪽지, 안 읽은 쪽지 ${count}통` : '쪽지'}
      className="relative grid size-8 shrink-0 place-items-center rounded-full text-icon-muted hover:bg-bg-muted hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
    >
      <svg viewBox="0 0 24 24" className="size-5 fill-none stroke-current stroke-2" aria-hidden>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3.5 6.5 7.4 5.6a1.8 1.8 0 0 0 2.2 0l7.4-5.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {count > 0 && <UnreadBadge className="absolute -top-1 -right-1.5">{unreadLetterLabel(count)}</UnreadBadge>}
    </Link>
  )
}
