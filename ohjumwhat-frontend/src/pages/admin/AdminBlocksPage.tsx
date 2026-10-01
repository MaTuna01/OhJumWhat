import { useState } from 'react'
import { ActionRow, EmptyRow, ListCard } from '../../components/AdminParts.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { formatDay } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { type AdminBlock, useAdminBlocks, useUnblock } from '../../queries/admin.ts'

/** Figma A07·DA07 차단 목록: 강제 탈퇴한 구글 계정과 차단 해제 */
export default function AdminBlocksPage() {
  const blocks = useAdminBlocks()
  const unblock = useUnblock()
  const now = useNow(60_000)
  const [releasing, setReleasing] = useState<AdminBlock | null>(null)
  useDocumentTitle('차단', '관리자 콘솔')

  return (
    <div className="space-y-4">
      {blocks.data && <p className="text-xs font-medium text-text-tertiary">차단한 계정 {blocks.data.length}개 · 최근 순</p>}
      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0">
          {blocks.isPending ? (
            <PageLoader />
          ) : blocks.isError ? (
            <p className="text-sm text-text-danger">{blocks.error.message}</p>
          ) : (
            <ListCard>
              {blocks.data.length === 0 && <EmptyRow>차단한 계정이 없어요.</EmptyRow>}
              {blocks.data.map((b) => (
                <ActionRow
                  key={b.id}
                  person={{ name: b.name }}
                  title={b.name}
                  subtitle={`${b.email} · ${formatDay(b.blockedAt, now)} · 차단: ${b.blockedByName ?? '알 수 없음'}`}
                  action={
                    <Button variant="ghost" className="shrink-0 py-1.5" onClick={() => setReleasing(b)}>
                      차단 해제
                    </Button>
                  }
                />
              ))}
            </ListCard>
          )}
        </div>
        <ul className="space-y-1 rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">
          <li>· 강제 탈퇴한 구글 계정은 다시 로그인할 수 없어요.</li>
          <li>· 차단을 풀면 같은 구글 계정으로 새 회원으로 가입할 수 있어요(예전 기록은 돌아오지 않아요).</li>
        </ul>
      </div>

      <ConfirmDialog
        open={releasing != null}
        onClose={() => {
          unblock.reset()
          setReleasing(null)
        }}
        onConfirm={() => releasing && unblock.mutate(releasing.id, { onSuccess: () => setReleasing(null) })}
        title={`${releasing?.name ?? ''} 님의 차단을 풀까요?`}
        confirmLabel="차단 해제"
        pending={unblock.isPending}
        error={unblock.error?.message}
      >
        <p>같은 구글 계정으로 다시 로그인하면 새 회원으로 가입돼요.</p>
      </ConfirmDialog>
    </div>
  )
}
