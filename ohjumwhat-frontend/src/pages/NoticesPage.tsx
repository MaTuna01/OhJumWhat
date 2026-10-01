import { useEffect, useRef } from 'react'
import NoticeBadge from '../components/NoticeBadge.tsx'
import NoticeBody from '../components/NoticeBody.tsx'
import { PageLoader } from '../components/PageState.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { formatMonthDay } from '../lib/time.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { type Notice, useMarkNoticesSeen, useNotices } from '../queries/notices.ts'

/**
 * Figma 09·D09 새 소식: 업데이트(배포 때 자동 게시)와 개발자 노트를 최신순으로 10개씩.
 * 목록을 받은 뒤 모두 읽은 것으로 하고(점·배너가 사라짐), 이번 방문 동안은 안 읽었던 글에 NEW를 남긴다.
 */
export default function NoticesPage() {
  useDocumentTitle('새 소식')
  const notices = useNotices()
  const { mutate: markSeen } = useMarkNoticesSeen()
  const marked = useRef(false)
  const loaded = notices.isSuccess

  useEffect(() => {
    if (!loaded || marked.current) return
    marked.current = true
    markSeen()
  }, [loaded, markSeen])

  const items = notices.data?.pages.flatMap((page) => page.notices) ?? []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">새 소식</h1>
        <p className="mt-1 text-sm text-text-tertiary lg:hidden">업데이트 소식과 개발자 노트를 모아 봐요</p>
      </div>

      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0 space-y-4">
          {notices.isPending ? (
            <PageLoader />
          ) : notices.isError ? (
            <p className="text-sm text-text-danger">{notices.error.message}</p>
          ) : items.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-10 text-center text-sm text-text-tertiary">
              아직 새 소식이 없어요.
            </p>
          ) : (
            <ul className="space-y-4">
              {items.map((notice) => (
                <li key={notice.id}>
                  <NoticeCard notice={notice} />
                </li>
              ))}
            </ul>
          )}
          {notices.hasNextPage && (
            <button
              type="button"
              onClick={() => notices.fetchNextPage()}
              disabled={notices.isFetchingNextPage}
              className={buttonClass('ghost', 'w-full')}
            >
              {notices.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
            </button>
          )}
        </div>

        {/* 데스크톱 사이드: 현재 버전. 모바일은 목록 아래 한 줄로 보여준다. */}
        <aside>
          <section className="hidden space-y-2 rounded-2xl border border-border-default bg-bg-surface p-5 lg:block">
            <h2 className="font-bold">현재 버전 v{__APP_VERSION__}</h2>
            <p className="text-sm text-text-tertiary">
              업데이트 소식은 배포할 때마다 자동으로 올라와요. 개발자 노트는 점검처럼 알릴 일이 있을 때 써요.
            </p>
          </section>
          <p className="text-center text-xs text-text-placeholder lg:hidden">현재 버전 v{__APP_VERSION__}</p>
        </aside>
      </div>
    </div>
  )
}

function NoticeCard({ notice }: { notice: Notice }) {
  return (
    <article className="rounded-2xl border border-border-default bg-bg-surface p-5">
      <div className="flex items-center gap-2">
        <NoticeBadge kind={notice.kind} version={notice.version} />
        <time dateTime={notice.publishedAt} className="text-xs text-text-tertiary">
          {formatMonthDay(notice.publishedAt)}
        </time>
        {notice.unread && <span className="ml-auto text-xs font-medium text-text-brand">NEW</span>}
      </div>
      <h2 className="mt-2.5 font-bold">{notice.title}</h2>
      <div className="mt-2.5">
        <NoticeBody body={notice.body} />
      </div>
    </article>
  )
}
