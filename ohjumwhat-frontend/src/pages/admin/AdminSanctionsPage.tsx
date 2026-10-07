import { type ReactNode, useState } from 'react'
import { Link } from 'react-router'
import Avatar from '../../components/Avatar.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import SanctionRow, { LiftSanctionDialog } from '../../components/SanctionRow.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import type { SanctionRow as SanctionRowData } from '../../lib/sanctions.ts'
import { ADMIN_LIST_LIMIT, useAdminStats } from '../../queries/admin.ts'
import { useAdminSanctions } from '../../queries/sanctions.ts'

type Status = 'active' | 'all'

/**
 * Figma A10·DA10 「제재」 탭: 기본은 진행 중(지금 제한이 걸린 제재), 「전체」는 기간 끝남·해제됨·경고·초기화만까지 최근 순(최대 100건).
 * 줄마다 사람(「회원 보기 ›」)과 SanctionRow이고, 진행 중인 제재는 여기서도 해제한다(A03-M6).
 */
export default function AdminSanctionsPage() {
  const [status, setStatus] = useState<Status>('active')
  const sanctions = useAdminSanctions(status)
  const stats = useAdminStats()
  const [lifting, setLifting] = useState<SanctionRowData | null>(null)
  useDocumentTitle('제재', '관리자 콘솔')

  const rows = sanctions.data ?? []
  const restrictedCount = stats.data?.restrictedUserCount ?? 0
  const summary =
    status === 'active'
      ? `지금 제한 중인 사람 ${new Set(rows.map((r) => r.userId)).size}명 · 최근 순`
      : rows.length >= ADMIN_LIST_LIMIT
        ? `제재 최근 ${ADMIN_LIST_LIMIT}건`
        : `제재 ${rows.length}건 · 최근 순`

  return (
    <div className="space-y-3">
      <div role="group" aria-label="제재 보기" className="flex gap-1.5">
        <Pill pressed={status === 'active'} onClick={() => setStatus('active')}>
          진행 중{restrictedCount > 0 && ` ${restrictedCount}`}
        </Pill>
        <Pill pressed={status === 'all'} onClick={() => setStatus('all')}>
          전체
        </Pill>
      </div>
      {sanctions.data && !sanctions.isPlaceholderData && <p className="text-xs font-medium text-text-tertiary">{summary}</p>}
      {sanctions.isPending ? (
        <PageLoader />
      ) : sanctions.isError ? (
        <p className="text-sm text-text-danger">{sanctions.error.message}</p>
      ) : rows.length === 0 ? (
        <p className="rounded-2xl border border-border-default bg-bg-surface py-8 text-center text-sm text-text-tertiary">
          {status === 'active' ? '지금 제한 중인 사람이 없어요.' : '제재 기록이 없어요.'}
        </p>
      ) : (
        <ul className="space-y-4 rounded-2xl border border-border-default bg-bg-surface p-4">
          {rows.map((row) => (
            <li key={row.id} className="space-y-2">
              <div className="flex items-center gap-2">
                <Avatar name={row.userName} imageUrl={row.userProfileImageUrl} size="sm" />
                <span className="truncate text-sm font-bold">{row.userName}</span>
                <span className="min-w-0 truncate text-xs text-text-tertiary">{row.userEmail}</span>
                <Link to={`/admin/users/${row.userId}`} className="ml-auto shrink-0 text-xs font-medium text-text-brand hover:underline">
                  회원 보기 ›
                </Link>
              </div>
              <SanctionRow row={row} onLift={() => setLifting(row)} />
            </li>
          ))}
        </ul>
      )}
      <LiftSanctionDialog row={lifting} onClose={() => setLifting(null)} />
    </div>
  )
}

function Pill({ pressed, onClick, children }: { pressed: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium focus-visible:outline-2 focus-visible:outline-border-brand ${
        pressed ? 'border-border-brand bg-bg-brand-soft text-text-brand-strong' : 'border-border-default bg-bg-surface text-text-secondary'
      }`}
    >
      {children}
    </button>
  )
}
