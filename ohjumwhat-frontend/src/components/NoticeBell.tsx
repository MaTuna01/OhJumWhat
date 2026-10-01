import { Link } from 'react-router'
import { useUnreadNotices } from '../queries/notices.ts'

/** 상단 바 새 소식(Figma TopBar 「새 소식 점」). 안 읽은 공지가 있으면 점을 띄운다. */
export default function NoticeBell() {
  const { data } = useUnreadNotices()
  const count = data?.count ?? 0

  return (
    <Link
      to="/notices"
      aria-label={count > 0 ? `새 소식, 안 읽은 소식 ${count}개` : '새 소식'}
      className="relative grid size-8 shrink-0 place-items-center rounded-full text-icon-muted hover:bg-bg-muted hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
    >
      <svg viewBox="0 0 20 20" className="size-5" aria-hidden>
        <path
          fill="currentColor"
          d="M10 2a6 6 0 00-6 6v3.586l-.707.707A1 1 0 004 14h12a1 1 0 00.707-1.707L16 11.586V8a6 6 0 00-6-6zM10 18a3 3 0 01-3-3h6a3 3 0 01-3 3z"
        />
      </svg>
      {count > 0 && <span className="absolute top-[5px] right-[5px] size-2 rounded-full bg-bg-brand ring-[1.5px] ring-bg-surface" />}
    </Link>
  )
}
