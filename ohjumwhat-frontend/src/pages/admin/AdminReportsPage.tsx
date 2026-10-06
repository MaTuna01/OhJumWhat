import { type ReactNode, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import Badge from '../../components/Badge.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import { EmptyRow, ListCard } from '../../components/AdminParts.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { ApiError } from '../../lib/api.ts'
import { RESTRICTED_TEXT } from '../../lib/guestbook.ts'
import { formatClock, formatDay, formatDayTime } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import {
  type AdminGuestbookReport,
  type AdminLetterReport,
  useAdminGuestbookReports,
  useAdminLetterReports,
  useAdminStats,
  useDismissGuestbookReport,
  useResolveLetterReport,
  useRestrictGuestbookReport,
} from '../../queries/admin.ts'

type Status = 'open' | 'all'
type ReportType = 'letter' | 'guestbook'

/** 서버가 한 번에 주는 신고 수(LetterService·GuestbookService의 관리자 목록 한도) */
const REPORT_LIMIT = 100

/**
 * Figma A09·DA09 쪽지 신고, A09b·DA09b 방명록 신고. 위의 [쪽지 N][방명록 N](처리 전 수)으로 바꾸고 주소 ?type=guestbook으로 기억한다.
 * 쪽지는 받은 사람이, 방명록은 프로필 주인이 신고한 글만 본다(신고되지 않은 글은 관리자도 볼 수 없다).
 */
export default function AdminReportsPage() {
  const [params, setParams] = useSearchParams()
  const type: ReportType = params.get('type') === 'guestbook' ? 'guestbook' : 'letter'
  const [status, setStatus] = useState<Status>('open')
  const stats = useAdminStats()
  const now = useNow(60_000)
  useDocumentTitle('신고', '관리자 콘솔')

  const openCounts: Record<ReportType, number | undefined> = {
    letter: stats.data?.openLetterReportCount,
    guestbook: stats.data?.openGuestbookReportCount,
  }

  return (
    <div className="space-y-3">
      <div role="group" aria-label="신고 종류" className="flex gap-1.5">
        {(['letter', 'guestbook'] as const).map((value) => (
          <Pill key={value} pressed={type === value} onClick={() => setParams(value === 'guestbook' ? { type: 'guestbook' } : {}, { replace: true })}>
            {value === 'letter' ? '쪽지' : '방명록'}
            {(openCounts[value] ?? 0) > 0 && ` ${openCounts[value]}`}
          </Pill>
        ))}
      </div>
      <div role="group" aria-label="신고 보기" className="flex gap-1.5">
        {(['open', 'all'] as const).map((value) => (
          <Pill key={value} pressed={status === value} onClick={() => setStatus(value)}>
            {value === 'open' ? '처리 전' : '전체'}
          </Pill>
        ))}
      </div>
      {type === 'guestbook' ? <GuestbookReports status={status} now={now} /> : <LetterReports status={status} now={now} />}
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

/** 목록 위의 건수: 「방명록 신고 5개 · 처리 전 3개 · 최근 순」(처리 전만 볼 때는 「처리 전 방명록 신고 3개 · 최근 순」) */
function summary(kind: string, status: Status, total: number, open: number): string {
  if (total >= REPORT_LIMIT) return `${status === 'open' ? '처리 전 ' : ''}${kind} 신고 최근 ${REPORT_LIMIT}개`
  return status === 'open' ? `처리 전 ${kind} 신고 ${total}개 · 최근 순` : `${kind} 신고 ${total}개 · 처리 전 ${open}개 · 최근 순`
}

function Summary({ children }: { children: string }) {
  return <p className="text-xs font-medium text-text-tertiary">{children}</p>
}

/** 오른쪽 사이드(모바일은 목록 아래)의 안내 */
function Notes({ children }: { children: ReactNode }) {
  return <ul className="space-y-1 rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">{children}</ul>
}

/** 쪽지 신고: 익명 쪽지도 실제 보낸 사람이 보이고, 「보낸 사람 보기」에서 강제 탈퇴할 수 있다. */
function LetterReports({ status, now }: { status: Status; now: number }) {
  const reports = useAdminLetterReports(status)
  const resolve = useResolveLetterReport()

  return (
    <>
      {reports.data && !reports.isPlaceholderData && (
        <Summary>{summary('쪽지', status, reports.data.length, reports.data.filter((r) => !r.resolvedAt).length)}</Summary>
      )}
      <div className={`flex flex-col gap-4 pt-1 ${columnsClass}`}>
        <div className="min-w-0">
          {reports.isPending ? (
            <PageLoader />
          ) : reports.isError ? (
            <p className="text-sm text-text-danger">{reports.error.message}</p>
          ) : (
            <ListCard>
              {reports.data.length === 0 && <EmptyRow>{status === 'open' ? '처리할 신고가 없어요.' : '신고가 없어요.'}</EmptyRow>}
              {reports.data.map((report) => (
                <LetterReportRow key={report.id} report={report} now={now} resolving={resolve.isPending} onResolve={() => resolve.mutate(report.id)} />
              ))}
            </ListCard>
          )}
          {resolve.error && (
            <p role="alert" className="mt-2 text-sm text-text-danger">
              {resolve.error instanceof ApiError ? resolve.error.message : '처리하지 못했어요.'}
            </p>
          )}
        </div>
        <Notes>
          <li>· 받은 사람이 신고한 쪽지만 볼 수 있어요. 신고되지 않은 쪽지는 관리자도 볼 수 없어요.</li>
          <li>· 익명 쪽지도 실제 보낸 사람이 보여요. 「보낸 사람 보기」에서 강제 탈퇴할 수 있고, 강제 탈퇴하면 그 사람의 열린 신고는 처리 완료가 돼요.</li>
        </Notes>
      </div>
    </>
  )
}

function LetterReportRow({ report, now, resolving, onResolve }: { report: AdminLetterReport; now: number; resolving: boolean; onResolve: () => void }) {
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
        신고한 사람(받은 사람) · {report.recipientName ?? '탈퇴한 사용자'} · {report.organizationName ?? '삭제된 조직'} · {formatDayTime(report.sentAt, now)}
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

/** 방명록 신고(A09b·DA09b): 「문제 없음」은 바로, 「글 제한」은 확인 창(A09b-M)을 거친다. */
function GuestbookReports({ status, now }: { status: Status; now: number }) {
  const reports = useAdminGuestbookReports(status)
  const restrict = useRestrictGuestbookReport()
  const dismiss = useDismissGuestbookReport()
  const [restricting, setRestricting] = useState<AdminGuestbookReport | null>(null)
  const closeRestrict = () => {
    restrict.reset()
    setRestricting(null)
  }

  return (
    <>
      {reports.data && !reports.isPlaceholderData && (
        <Summary>{summary('방명록', status, reports.data.length, reports.data.filter((r) => r.resolution === null).length)}</Summary>
      )}
      <div className={`flex flex-col gap-4 pt-1 ${columnsClass}`}>
        <div className="min-w-0">
          {reports.isPending ? (
            <PageLoader />
          ) : reports.isError ? (
            <p className="text-sm text-text-danger">{reports.error.message}</p>
          ) : (
            <ListCard>
              {reports.data.length === 0 && <EmptyRow>{status === 'open' ? '처리할 신고가 없어요.' : '신고가 없어요.'}</EmptyRow>}
              {reports.data.map((report) => (
                <GuestbookReportRow
                  key={report.id}
                  report={report}
                  now={now}
                  busy={restrict.isPending || dismiss.isPending}
                  onDismiss={() => dismiss.mutate(report.id)}
                  onRestrict={() => {
                    restrict.reset()
                    setRestricting(report)
                  }}
                />
              ))}
            </ListCard>
          )}
          {dismiss.error && (
            <p role="alert" className="mt-2 text-sm text-text-danger">
              {dismiss.error instanceof ApiError ? dismiss.error.message : '처리하지 못했어요.'}
            </p>
          )}
        </div>
        <Notes>
          <li>· 방명록 신고는 프로필 주인만 할 수 있어요. 「글 제한」을 누르면 모두에게 「{RESTRICTED_TEXT}」로 보이고, 작성자에게 경고 안내가 가요.</li>
          <li>· 신고된 뒤 글이 지워졌으면(주인이든 작성자든) 「삭제됨」이 붙어요. 원문은 여기에서 그대로 볼 수 있고, 처리도 똑같이 해요.</li>
        </Notes>
      </div>
      <ConfirmDialog
        open={restricting !== null}
        onClose={closeRestrict}
        onConfirm={() => restricting && restrict.mutate(restricting.id, { onSuccess: closeRestrict })}
        title="이 글을 제한할까요?"
        confirmLabel="글 제한"
        danger
        pending={restrict.isPending}
        error={restrict.error ? (restrict.error instanceof ApiError ? restrict.error.message : '제한하지 못했어요.') : null}
      >
        모두에게 「{RESTRICTED_TEXT}」로 보이고, 작성자에게 경고 안내가 가요. 되돌릴 수 없어요.
      </ConfirmDialog>
    </>
  )
}

type GuestbookRowProps = { report: AdminGuestbookReport; now: number; busy: boolean; onDismiss: () => void; onRestrict: () => void }

function GuestbookReportRow({ report, now, busy, onDismiss, onRestrict }: GuestbookRowProps) {
  // 쓴 날 신고했으면 신고 시각은 시:분만(「작성 10월 6일 오후 12:30 · 신고 오후 1:02」)
  const sameDay = formatDay(report.reportedAt, now) === formatDay(report.writtenAt, now)

  return (
    <li className="space-y-2 py-4">
      <div className="flex flex-wrap items-center gap-1.5">
        {report.resolution === 'RESTRICTED' ? (
          <Badge tone="danger">글 제한</Badge>
        ) : report.resolution === 'DISMISSED' ? (
          <Badge tone="neutral">문제 없음</Badge>
        ) : (
          <Badge tone="warning">처리 전</Badge>
        )}
        {report.deletedAt && <Badge tone="neutral">삭제됨</Badge>}
        <span className="text-xs text-text-tertiary">{formatDayTime(report.reportedAt, now)} 신고</span>
      </div>
      <p className="rounded-lg bg-bg-subtle px-3 py-2.5 text-sm break-words">{report.body}</p>
      <p className="text-xs font-medium">
        작성자 {report.authorName ?? '탈퇴한 사용자'} · 방명록 주인(신고) {report.ownerName ?? '탈퇴한 사용자'}
      </p>
      <p className="text-xs text-text-secondary">
        작성 {formatDayTime(report.writtenAt, now)} · 신고 {sameDay ? formatClock(report.reportedAt) : formatDayTime(report.reportedAt, now)}
      </p>
      <p className="text-xs text-text-secondary">{report.reason ? `사유: ${report.reason}` : '사유 없음'}</p>
      <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-2 pt-1">
        {report.resolution && (
          <span className="mr-auto text-xs text-text-tertiary">
            {report.resolvedByName ? `관리자 ${report.resolvedByName}` : '관리자'}
            {report.resolvedAt && ` · ${formatDayTime(report.resolvedAt, now)}`} 처리
          </span>
        )}
        {report.authorId != null && (
          <Link to={`/admin/users/${report.authorId}`} className="text-sm font-medium text-text-brand hover:underline">
            작성자 보기 ›
          </Link>
        )}
        {report.resolution === null && (
          <>
            <Button variant="secondary" disabled={busy} onClick={onDismiss}>
              문제 없음
            </Button>
            <Button variant="danger" disabled={busy} onClick={onRestrict}>
              글 제한
            </Button>
          </>
        )}
      </div>
    </li>
  )
}
