import { useState } from 'react'
import { ActionRow, EmptyRow, ListCard } from '../../components/AdminParts.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import NoticeFormModal from '../../components/NoticeFormModal.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { formatMonthDay } from '../../lib/time.ts'
import { buttonClass, columnsClass } from '../../lib/ui.ts'
import { useDeleteNotice } from '../../queries/admin.ts'
import { type Notice, useNotices } from '../../queries/notices.ts'

/** Figma A08·DA08 공지: 업데이트(배포 때 자동 게시)와 개발자 노트. 개발자 노트만 여기서 쓰고 고치고 지운다. */
export default function AdminNoticesPage() {
  const notices = useNotices()
  const remove = useDeleteNotice()
  const [editing, setEditing] = useState<Notice | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Notice | null>(null)
  useDocumentTitle('공지', '관리자 콘솔')
  const items = notices.data?.pages.flatMap((page) => page.notices) ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-medium text-text-tertiary">
          {notices.isSuccess && `새 소식 ${items.length}개${notices.hasNextPage ? ' 이상' : ''} · 최신순`}
        </p>
        <Button onClick={() => setEditing('new')}>글쓰기</Button>
      </div>
      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0 space-y-2">
          {notices.isPending ? (
            <PageLoader />
          ) : notices.isError ? (
            <p className="text-sm text-text-danger">{notices.error.message}</p>
          ) : (
            <ListCard>
              {items.length === 0 && <EmptyRow>아직 새 소식이 없어요.</EmptyRow>}
              {items.map((notice) => (
                <ActionRow
                  key={notice.id}
                  title={notice.title}
                  subtitle={
                    notice.kind === 'RELEASE'
                      ? `업데이트 v${notice.version} · ${formatMonthDay(notice.publishedAt)} · 자동 게시`
                      : `개발자 노트 · ${formatMonthDay(notice.publishedAt)}`
                  }
                  action={
                    notice.kind === 'NOTE' && (
                      <div className="flex shrink-0">
                        <Button variant="ghost" className="py-1.5" onClick={() => setEditing(notice)}>
                          수정
                        </Button>
                        <Button variant="ghost" className="py-1.5" onClick={() => setDeleting(notice)}>
                          삭제
                        </Button>
                      </div>
                    )
                  }
                />
              ))}
            </ListCard>
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
        <ul className="space-y-1 rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">
          <li>· 업데이트 글은 배포할 때 저장소의 release-notes 파일에서 자동으로 올라와요. 고치려면 파일을 고쳐서 다시 배포해요.</li>
          <li>· 글을 고쳐도 다시 알리지 않아요(게시 시각은 그대로). 본문은 일반 텍스트(빈 줄 = 문단, &quot;- &quot; = 목록).</li>
        </ul>
      </div>

      <NoticeFormModal notice={editing === 'new' ? null : editing} open={editing != null} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={deleting != null}
        onClose={() => {
          remove.reset()
          setDeleting(null)
        }}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })}
        title="이 새 소식을 지울까요?"
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        error={remove.error?.message}
      >
        <p>「{deleting?.title}」 글이 모든 회원의 새 소식에서 사라져요.</p>
      </ConfirmDialog>
    </div>
  )
}
