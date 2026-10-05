import { useState } from 'react'
import { Link } from 'react-router'
import Badge from '../../components/Badge.tsx'
import Button from '../../components/Button.tsx'
import { EmptyRow, ListCard } from '../../components/AdminParts.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { ApiError } from '../../lib/api.ts'
import { formatDayTime } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { type AdminLetterReport, useAdminLetterReports, useResolveLetterReport } from '../../queries/admin.ts'

type Status = 'open' | 'all'

/** 서버가 한 번에 주는 신고 수(LetterService.ADMIN_REPORT_LIMIT) */
const REPORT_LIMIT = 100

/**
 * Figma A09·DA09 쪽지 신고: 받은 사람이 신고한 쪽지만 본다(신고되지 않은 쪽지는 관리자도 볼 수 없다).
 * 익명 쪽지도 실제 보낸 사람이 보이고, 「보낸 사람 보기」에서 강제 탈퇴할 수 있다.
 */
export default function AdminReportsPage() {
  const [status, setStatus] = useState<Status>('open')
  const reports = useAdminLetterReports(status)
  const resolve = useResolveLetterReport()
  const now = useNow(60_000)
  useDocumentTitle('신고', '관리자 콘솔')

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div role="group" aria-label="신고 보기" className="flex gap-1.5">
          {(['open', 'all'] as const).map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={status === value}
              onClick={() => setStatus(value)}
              className={`rounded-full border px-3 py-1 text-xs font-medium focus-visible:outline-2 focus-visible:outline-border-brand ${
                status === value ? 'border-border-brand bg-bg-brand-soft text-text-brand-strong' : 'border-border-default bg-bg-surface text-text-secondary'
              }`}
            >
              {value === 'open' ? '처리 전' : '전체'}
            </button>
          ))}
        </div>
        {reports.data && !reports.isPlaceholderData && (
          <p className="text-xs font-medium text-text-tertiary">
            {status === 'open' ? '처리 전' : '전체'} 신고 {reports.data.length >= REPORT_LIMIT ? `최근 ${REPORT_LIMIT}개` : `${reports.data.length}개 · 최근 순`}
          </p>
        )}
      </div>
      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0">
          {reports.isPending ? (
            <PageLoader />
          ) : reports.isError ? (
            <p className="text-sm text-text-danger">{reports.error.message}</p>
          ) : (
            <ListCard>
              {reports.data.length === 0 && <EmptyRow>{status === 'open' ? '처리할 신고가 없어요.' : '신고가 없어요.'}</EmptyRow>}
              {reports.data.map((report) => (
                <ReportRow key={report.id} report={report} now={now} resolving={resolve.isPending} onResolve={() => resolve.mutate(report.id)} />
              ))}
            </ListCard>
          )}
          {resolve.error && (
            <p role="alert" className="mt-2 text-sm text-text-danger">
              {resolve.error instanceof ApiError ? resolve.error.message : '처리하지 못했어요.'}
            </p>
          )}
        </div>
        <ul className="space-y-1 rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">
          <li>· 받은 사람이 신고한 쪽지만 볼 수 있어요. 신고되지 않은 쪽지는 관리자도 볼 수 없어요.</li>
          <li>· 익명 쪽지도 실제 보낸 사람이 보여요. 「보낸 사람 보기」에서 강제 탈퇴할 수 있고, 강제 탈퇴하면 그 사람의 열린 신고는 처리 완료가 돼요.</li>
        </ul>
      </div>
    </div>
  )
}

function ReportRow({ report, now, resolving, onResolve }: { report: AdminLetterReport; now: number; resolving: boolean; onResolve: () => void }) {
  return (
    <li className="space-y-2 py-3.5">
      <div className="flex items-center gap-1.5">
        {report.resolvedAt ? <Badge tone="neutral">처리 완료</Badge> : <Badge tone="warning">처리 전</Badge>}
        <span className="text-xs text-text-tertiary">{formatDayTime(report.reportedAt, now)} 신고</span>
      </div>
      <p className="rounded-lg bg-bg-subtle px-3 py-2.5 text-sm break-words whitespace-pre-wrap">{report.body}</p>
      <p className="flex flex-wrap items-center gap-1.5 text-xs font-medium">
        보낸 사람 · {report.senderName ? `${report.senderName} (${report.senderEmail})` : '탈퇴한 사용자'}
        {report.anonymous && <Badge tone="brand">익명으로 보냄</Badge>}
      </p>
      <p className="text-xs text-text-secondary">
        신고한 사람(받은 사람) · {report.recipientName} · {report.organizationName ?? '삭제된 조직'} · {formatDayTime(report.sentAt, now)}
      </p>
      <p className="text-xs text-text-secondary">{report.reason ? `사유 · ${report.reason}` : '사유 없음'}</p>
      <div className="flex items-center justify-end gap-3">
        {report.senderId != null && (
          <Link to={`/admin/users/${report.senderId}`} className="text-sm font-medium text-text-brand hover:underline">
            보낸 사람 보기 ›
          </Link>
        )}
        {report.resolvedAt ? (
          <span className="text-xs text-text-tertiary">
            {report.resolvedByName ?? '관리자'} · {formatDayTime(report.resolvedAt, now)} 처리
          </span>
        ) : (
          <Button variant="secondary" className="py-1.5" disabled={resolving} onClick={onResolve}>
            처리 완료
          </Button>
        )}
      </div>
    </li>
  )
}
